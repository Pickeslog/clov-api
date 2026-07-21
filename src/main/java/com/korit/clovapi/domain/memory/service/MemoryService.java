package com.korit.clovapi.domain.memory.service;

import com.korit.clovapi.domain.memory.dto.CommentResponse;
import com.korit.clovapi.domain.memory.dto.CommentsResponse;
import com.korit.clovapi.domain.memory.dto.CommitImageRequest;
import com.korit.clovapi.domain.memory.dto.CreateCommentRequest;
import com.korit.clovapi.domain.memory.dto.CreateMemoryRequest;
import com.korit.clovapi.domain.memory.dto.MemoryDetailResponse;
import com.korit.clovapi.domain.memory.dto.MemoryFeedResponse;
import com.korit.clovapi.domain.memory.dto.MemoryImageResponse;
import com.korit.clovapi.domain.memory.dto.MemoryImagesResponse;
import com.korit.clovapi.domain.memory.dto.MemorySummaryResponse;
import com.korit.clovapi.domain.memory.dto.ReorderImagesRequest;
import com.korit.clovapi.domain.memory.dto.UpdateMemoryRequest;
import com.korit.clovapi.domain.memory.entity.Memory;
import com.korit.clovapi.domain.memory.entity.MemoryComment;
import com.korit.clovapi.domain.memory.entity.MemoryImage;
import com.korit.clovapi.domain.memory.mapper.CommentMapper;
import com.korit.clovapi.domain.memory.mapper.MemoryImageMapper;
import com.korit.clovapi.domain.memory.mapper.MemoryMapper;
import com.korit.clovapi.domain.memory.mapper.ParticipantRow;
import com.korit.clovapi.domain.room.service.RoomService;
import com.korit.clovapi.global.dto.PresignRequest;
import com.korit.clovapi.global.dto.PresignResponse;
import com.korit.clovapi.global.dto.UserSummaryResponse;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import com.korit.clovapi.global.storage.StoragePresigner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class MemoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    // 쿼터 상한(memory당 장수) — 계약 미정, 리더 확정 시 이 값만 교체(#45).
    private static final int MAX_IMAGES_PER_MEMORY = 10;

    private final MemoryMapper memoryMapper;
    private final RoomService roomService;
    private final CommentMapper commentMapper;
    private final MemoryImageMapper memoryImageMapper;
    private final StoragePresigner storagePresigner;

    public MemoryService(MemoryMapper memoryMapper, RoomService roomService, CommentMapper commentMapper,
                         MemoryImageMapper memoryImageMapper, StoragePresigner storagePresigner) {
        this.memoryMapper = memoryMapper;
        this.roomService = roomService;
        this.commentMapper = commentMapper;
        this.memoryImageMapper = memoryImageMapper;
        this.storagePresigner = storagePresigner;
    }

    @Transactional
    public MemoryDetailResponse createFromPlan(long planId, long userId, CreateMemoryRequest request) {
        long roomId = memoryMapper.findPlanRoomId(planId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        roomService.assertActiveMember(roomId, userId);
        String memoryStatus = memoryMapper.findPlanMemoryStatus(planId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if ("NONE".equals(memoryStatus)) {
            throw new DomainException(ErrorCode.PLAN_NOT_COMPLETED);
        }
        if (memoryMapper.findByPlanIdAndWriterId(planId, userId).isPresent()) {
            throw new DomainException(ErrorCode.MEMORY_ALREADY_WRITTEN);
        }

        Memory memory = buildMemory(roomId, planId, userId, request);
        memoryMapper.insert(memory);
        saveTagsAndParticipants(memory.getId(), request);
        memoryMapper.updatePlanMemoryStatusWritten(planId);
        return getDetail(memory.getId(), userId);
    }

    @Transactional
    public MemoryDetailResponse createFree(long roomId, long userId, CreateMemoryRequest request) {
        roomService.assertActiveMember(roomId, userId);

        Memory memory = buildMemory(roomId, null, userId, request);
        memoryMapper.insert(memory);
        saveTagsAndParticipants(memory.getId(), request);
        return getDetail(memory.getId(), userId);
    }

    public MemoryFeedResponse findFeed(long roomId, long userId, String month, Long writerId, String tag,
                                       Long participantUserId, int page, int size) {
        roomService.assertActiveMember(roomId, userId);
        int pageSize = size > 0 ? size : DEFAULT_PAGE_SIZE;
        int offset = Math.max(page, 0) * pageSize;
        List<Memory> rows = memoryMapper.findFeed(roomId, month, writerId, tag, participantUserId, pageSize, offset);
        return new MemoryFeedResponse(rows.stream().map(MemorySummaryResponse::from).toList());
    }

    public MemoryDetailResponse getDetail(long memoryId, long userId) {
        Memory memory = findExisting(memoryId);
        roomService.assertActiveMember(memory.getRoomId(), userId);
        List<MemoryImageResponse> images = findImages(memoryId);
        List<String> tags = memoryMapper.findTags(memoryId);
        List<UserSummaryResponse> participants = memoryMapper.findParticipants(memoryId).stream()
                .map(this::toUserSummary)
                .toList();
        return MemoryDetailResponse.from(memory, images, tags, participants);
    }

    /**
     * 이미지 업로드용 presigned PUT URL 발급(작성자, 계약 §10·§4-3). 파일 저장/행 생성은 없고 서명만 한다.
     * 쿼터 초과 시 {@code 507 STORAGE_QUOTA_EXCEEDED}(커밋에서 최종 재검증).
     */
    public PresignResponse presignImage(long memoryId, long userId, PresignRequest request) {
        Memory memory = findExisting(memoryId);
        roomService.assertActiveMember(memory.getRoomId(), userId);
        assertWriter(memory, userId);
        assertQuotaAvailable(memoryId);
        String objectKey = "memories/%d/%s%s".formatted(
                memoryId, UUID.randomUUID(), StoragePresigner.extensionFor(request.contentType()));
        return PresignResponse.from(storagePresigner.presignPut(objectKey, request.contentType()));
    }

    /** 업로드 커밋 — memory_images 행 생성(작성자). sortOrder 미지정 시 마지막 순서로 덧붙인다. */
    @Transactional
    public MemoryImageResponse commitImage(long memoryId, long userId, CommitImageRequest request) {
        Memory memory = findExisting(memoryId);
        roomService.assertActiveMember(memory.getRoomId(), userId);
        assertWriter(memory, userId);
        assertQuotaAvailable(memoryId);

        MemoryImage image = new MemoryImage();
        image.setMemoryId(memoryId);
        image.setImageUrl(request.imageUrl());
        image.setSortOrder(request.sortOrder() != null ? request.sortOrder()
                : memoryImageMapper.countByMemoryId(memoryId));
        memoryImageMapper.insert(image);
        return MemoryImageResponse.from(memoryImageMapper.findById(image.getId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
    }

    /** 이미지 삭제(작성자). */
    @Transactional
    public void deleteImage(long imageId, long userId) {
        MemoryImage image = memoryImageMapper.findById(imageId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Memory memory = findExisting(image.getMemoryId());
        roomService.assertActiveMember(memory.getRoomId(), userId);
        assertWriter(memory, userId);
        memoryImageMapper.deleteById(imageId);
    }

    /** {@code imageIds} 순서대로 sort_order 재부여(작성자). 다른 memory의 이미지가 섞이면 {@code NOT_FOUND}. */
    @Transactional
    public MemoryImagesResponse reorderImages(long memoryId, long userId, ReorderImagesRequest request) {
        Memory memory = findExisting(memoryId);
        roomService.assertActiveMember(memory.getRoomId(), userId);
        assertWriter(memory, userId);

        List<String> imageIds = request.imageIds();
        for (int order = 0; order < imageIds.size(); order++) {
            long imageId = Long.parseLong(imageIds.get(order));
            MemoryImage image = memoryImageMapper.findById(imageId)
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            if (image.getMemoryId() != memoryId) {
                throw new DomainException(ErrorCode.NOT_FOUND);
            }
            memoryImageMapper.updateSortOrder(imageId, order);
        }
        return new MemoryImagesResponse(findImages(memoryId));
    }

    private List<MemoryImageResponse> findImages(long memoryId) {
        return memoryImageMapper.findByMemoryId(memoryId).stream()
                .map(MemoryImageResponse::from)
                .toList();
    }

    private void assertQuotaAvailable(long memoryId) {
        if (memoryImageMapper.countByMemoryId(memoryId) + 1 > MAX_IMAGES_PER_MEMORY) {
            throw new DomainException(ErrorCode.STORAGE_QUOTA_EXCEEDED);
        }
    }

    @Transactional
    public MemoryDetailResponse update(long memoryId, long userId, UpdateMemoryRequest request) {
        Memory memory = findExisting(memoryId);
        roomService.assertActiveMember(memory.getRoomId(), userId);
        assertWriter(memory, userId);

        memoryMapper.update(memoryId, request);
        if (request.has("tags")) {
            memoryMapper.deleteTags(memoryId);
            List<String> tags = request.getTags();
            if (tags != null && !tags.isEmpty()) {
                memoryMapper.insertTags(memoryId, tags);
            }
        }
        if (request.has("participantUserIds")) {
            memoryMapper.deleteParticipants(memoryId);
            List<String> participantUserIds = request.getParticipantUserIds();
            if (participantUserIds != null && !participantUserIds.isEmpty()) {
                memoryMapper.insertParticipants(memoryId, participantUserIds.stream().map(Long::parseLong).toList());
            }
        }
        return getDetail(memoryId, userId);
    }

    @Transactional
    public void delete(long memoryId, long userId) {
        Memory memory = findExisting(memoryId);
        roomService.assertActiveMember(memory.getRoomId(), userId);
        assertWriter(memory, userId);
        memoryMapper.softDelete(memoryId, LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public CommentResponse createComment(long memoryId, long userId, CreateCommentRequest request) {
        Memory memory = findExisting(memoryId);
        roomService.assertActiveMember(memory.getRoomId(), userId);

        MemoryComment comment = new MemoryComment();
        comment.setMemoryId(memoryId);
        comment.setWriterId(userId);
        comment.setContent(request.content());
        commentMapper.insert(comment);
        return CommentResponse.from(commentMapper.findById(comment.getId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
    }

    public CommentsResponse findComments(long memoryId, long userId) {
        Memory memory = findExisting(memoryId);
        roomService.assertActiveMember(memory.getRoomId(), userId);
        return new CommentsResponse(commentMapper.findByMemoryId(memoryId).stream()
                .map(CommentResponse::from)
                .toList());
    }

    @Transactional
    public void deleteComment(long commentId, long userId) {
        MemoryComment comment = commentMapper.findById(commentId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (comment.getWriterId() != userId) {
            throw new DomainException(ErrorCode.NOT_WRITER);
        }
        commentMapper.delete(commentId);
    }

    private Memory findExisting(long memoryId) {
        return memoryMapper.findById(memoryId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private void assertWriter(Memory memory, long userId) {
        if (memory.getWriterId() != userId) {
            throw new DomainException(ErrorCode.NOT_WRITER);
        }
    }

    private UserSummaryResponse toUserSummary(ParticipantRow row) {
        return new UserSummaryResponse(String.valueOf(row.getUserId()), row.getNickname(), row.getProfileImageUrl());
    }

    private Memory buildMemory(long roomId, Long planId, long writerId, CreateMemoryRequest request) {
        Memory memory = new Memory();
        memory.setRoomId(roomId);
        memory.setPlanId(planId);
        memory.setWriterId(writerId);
        memory.setTitle(request.title());
        memory.setContent(request.content());
        memory.setMemoryDate(request.memoryDate());
        return memory;
    }

    private void saveTagsAndParticipants(long memoryId, CreateMemoryRequest request) {
        if (request.tags() != null && !request.tags().isEmpty()) {
            memoryMapper.insertTags(memoryId, request.tags());
        }
        if (request.participantUserIds() != null && !request.participantUserIds().isEmpty()) {
            memoryMapper.insertParticipants(memoryId,
                    request.participantUserIds().stream().map(Long::parseLong).toList());
        }
    }
}
