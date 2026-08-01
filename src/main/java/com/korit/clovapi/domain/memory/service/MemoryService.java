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
import com.korit.clovapi.domain.memory.dto.UpdateCommentRequest;
import com.korit.clovapi.domain.memory.dto.UpdateMemoryRequest;
import com.korit.clovapi.domain.memory.entity.Memory;
import com.korit.clovapi.domain.memory.entity.MemoryComment;
import com.korit.clovapi.domain.memory.entity.MemoryImage;
import com.korit.clovapi.domain.memory.mapper.CommentMapper;
import com.korit.clovapi.domain.memory.mapper.MemoryCover;
import com.korit.clovapi.domain.memory.mapper.MemoryImageMapper;
import com.korit.clovapi.domain.memory.mapper.MemoryMapper;
import com.korit.clovapi.domain.memory.mapper.ParticipantRow;
import com.korit.clovapi.domain.notification.service.NotificationService;
import com.korit.clovapi.domain.room.service.ExpService;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MemoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    // 쿼터 상한(memory당 장수) — 8로 확정(리더 결정 2026-07-30,
    // screen-spec-source/03-memory-feed-screen.md §입력 제약). 프로토타입은 30이지만 프로덕션은
    // 추억마다 R2에 실제 파일이 올라가서 저장 쿼터 도달 속도가 4배 가까이 빨라진다.
    // 프론트(clov-web Feed.jsx MEMORY_PHOTO_LIMIT)와 같은 값이어야 한다 — 여기가 낮으면
    // 화면에서 고를 수 있는 사진이 업로드에서 507로 튕긴다(실제로 프론트 15 vs 여기 10이었다).
    private static final int MAX_IMAGES_PER_MEMORY = 8;

    private final MemoryMapper memoryMapper;
    private final RoomService roomService;
    private final CommentMapper commentMapper;
    private final MemoryImageMapper memoryImageMapper;
    private final StoragePresigner storagePresigner;
    private final ExpService expService;
    private final NotificationService notificationService;

    public MemoryService(MemoryMapper memoryMapper, RoomService roomService, CommentMapper commentMapper,
                         MemoryImageMapper memoryImageMapper, StoragePresigner storagePresigner,
                         ExpService expService, NotificationService notificationService) {
        this.memoryMapper = memoryMapper;
        this.roomService = roomService;
        this.commentMapper = commentMapper;
        this.memoryImageMapper = memoryImageMapper;
        this.storagePresigner = storagePresigner;
        this.expService = expService;
        this.notificationService = notificationService;
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

        // clov-api#98 — findByPlanIdAndWriterId는 이제 삭제분도 본다. 활성 행이 있으면 기존대로 막고,
        // 삭제된 행만 있으면 되살린다(지웠다 다시 쓰기가 500 없이 동작해야 한다). 이 경우 EXP/알림은
        // 다시 주지 않는다 — 같은 행이 이미 처음 작성 때 받았다(재작성마다 EXP를 또 주면 삭제·재작성을
        // 반복해 EXP를 무한정 채굴할 수 있다).
        Memory existing = memoryMapper.findByPlanIdAndWriterId(planId, userId).orElse(null);
        if (existing != null) {
            if (existing.getDeletedAt() == null) {
                throw new DomainException(ErrorCode.MEMORY_ALREADY_WRITTEN);
            }
            memoryMapper.revive(existing.getId(), request.title(), request.content(), request.memoryDate(),
                    LocalDateTime.now(ZoneOffset.UTC));
            // 삭제 전 사진·친구 메시지는 새 기록에 이어받지 않는다 — 완전히 다른 내용으로 다시
            // 쓴 추억에 옛 첨부물이 그대로 붙어 있으면 맥락이 안 맞는다.
            memoryImageMapper.deleteByMemoryId(existing.getId());
            commentMapper.deleteByMemoryId(existing.getId());
            memoryMapper.deleteTags(existing.getId());
            memoryMapper.deleteParticipants(existing.getId());
            saveTagsAndParticipants(existing.getId(), request);
            memoryMapper.updatePlanMemoryStatusWritten(planId);
            return getDetail(existing.getId(), userId);
        }

        Memory memory = buildMemory(roomId, planId, userId, request);
        memoryMapper.insert(memory);
        saveTagsAndParticipants(memory.getId(), request);
        memoryMapper.updatePlanMemoryStatusWritten(planId);
        expService.grant(roomId, userId, ExpService.ACTION_MEMORY_WRITE,
                ExpService.memoryWriteExp(request.content()), memory.getId());
        notificationService.fanOut(roomId, userId, NotificationService.TYPE_FRIEND,
                NotificationService.SUB_MEMORY_WRITE, memory.getId(), null);
        return getDetail(memory.getId(), userId);
    }

    @Transactional
    public MemoryDetailResponse createFree(long roomId, long userId, CreateMemoryRequest request) {
        roomService.assertActiveMember(roomId, userId);

        Memory memory = buildMemory(roomId, null, userId, request);
        memoryMapper.insert(memory);
        saveTagsAndParticipants(memory.getId(), request);
        expService.grant(roomId, userId, ExpService.ACTION_MEMORY_WRITE,
                ExpService.memoryWriteExp(request.content()), memory.getId());
        notificationService.fanOut(roomId, userId, NotificationService.TYPE_FRIEND,
                NotificationService.SUB_MEMORY_WRITE, memory.getId(), null);
        return getDetail(memory.getId(), userId);
    }

    public MemoryFeedResponse findFeed(long roomId, long userId, String month, Long writerId, String tag,
                                       Long participantUserId, int page, int size) {
        roomService.assertActiveMember(roomId, userId);
        int pageSize = size > 0 ? size : DEFAULT_PAGE_SIZE;
        int offset = Math.max(page, 0) * pageSize;
        List<Memory> rows = memoryMapper.findFeed(roomId, month, writerId, tag, participantUserId, pageSize, offset);
        if (rows.isEmpty()) {
            return new MemoryFeedResponse(List.of());
        }
        // 대표 이미지·참여자를 memoryIds로 한 번씩만 조회해 매핑(N+1 회피).
        List<Long> memoryIds = rows.stream().map(Memory::getId).toList();
        Map<Long, MemoryCover> covers = memoryImageMapper.findCoverInfoByMemoryIds(memoryIds).stream()
                .collect(Collectors.toMap(MemoryCover::getMemoryId, cover -> cover, (a, b) -> a));
        Map<Long, List<UserSummaryResponse>> participants = memoryMapper.findParticipantsByMemoryIds(memoryIds).stream()
                .collect(Collectors.groupingBy(ParticipantRow::getMemoryId,
                        Collectors.mapping(this::toUserSummary, Collectors.toList())));
        return new MemoryFeedResponse(rows.stream()
                .map(memory -> {
                    MemoryCover cover = covers.get(memory.getId());
                    return MemorySummaryResponse.from(memory,
                            cover == null ? null : cover.getImageUrl(),
                            cover == null ? 0 : cover.getImageCount(),
                            participants.getOrDefault(memory.getId(), List.of()));
                })
                .toList());
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
        expService.grantMemoryImageBonus(memory.getRoomId(), userId, memoryId);
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

        if (request.has("planId")) {
            updatePlanLink(memory, userId, request.getPlanId());
        }

        // planId만 보내는 요청(약속 연결 변경/해제 전용)도 있어서(clov-api#98), title·content·
        // memoryDate가 하나도 없으면 이 UPDATE를 건너뛴다 — <set>에 채울 게 없으면 MyBatis가
        // "UPDATE memories SET WHERE id=?"를 만들어 SQL 문법 오류가 난다(실제로 재현됨).
        if (request.has("title") || request.has("content") || request.has("memoryDate")) {
            memoryMapper.update(memoryId, request);
        }
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
        revertPlanStatusIfEmpty(memory.getPlanId());
    }

    /**
     * 약속 연결 추억이 전부 삭제/해제되면(clov-api#98) memory_status를 WRITTEN → CANDIDATE로
     * 되돌린다. 다른 멤버가 그 약속에 남긴 추억이 하나라도 살아 있으면 손대지 않는다 — 우정공간은
     * 한 약속에 여러 명이 각자 기록을 남기는 구조라(핵심 원칙), 한 사람이 지웠다고 약속 전체가
     * "미기록"이 되면 안 된다. NONE으로는 절대 되돌리지 않는다 — NONE은 "약속이 아직 완료 안 됨"이고
     * 이 경우는 이미 완료된 상태다.
     */
    private void revertPlanStatusIfEmpty(Long planId) {
        if (planId == null) {
            return; // FREE MEMORY — 되돌릴 약속이 없다
        }
        if (memoryMapper.countActiveByPlanId(planId) == 0) {
            memoryMapper.updatePlanMemoryStatus(planId, "CANDIDATE");
        }
    }

    /**
     * 약속 연결 변경/해제(clov-api#98) — {@code newPlanIdStr}가 null이면 해제(FREE MEMORY로),
     * 아니면 그 약속으로 연결/이동한다. EXP는 건드리지 않는다 — createFromPlan·createFree가 이미
     * 같은 양을 주므로 연결/해제로 재화가 달라지면 안 된다.
     */
    private void updatePlanLink(Memory memory, long userId, String newPlanIdStr) {
        Long oldPlanId = memory.getPlanId();
        Long newPlanId = newPlanIdStr == null ? null : Long.parseLong(newPlanIdStr);

        if (Objects.equals(oldPlanId, newPlanId)) {
            return;
        }

        if (newPlanId != null) {
            long newPlanRoomId = memoryMapper.findPlanRoomId(newPlanId)
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            // 다른 방 약속은 존재 여부를 드러내지 않고 그냥 못 찾은 것처럼 처리한다.
            if (newPlanRoomId != memory.getRoomId()) {
                throw new DomainException(ErrorCode.NOT_FOUND);
            }
            String newPlanMemoryStatus = memoryMapper.findPlanMemoryStatus(newPlanId)
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            if ("NONE".equals(newPlanMemoryStatus)) {
                throw new DomainException(ErrorCode.PLAN_NOT_COMPLETED);
            }
            // 삭제분 포함 조회 — uk_memories_plan_writer가 deleted_at을 모르므로, 삭제된 행이라도
            // 남아 있으면 이 UPDATE가 유니크 제약을 건드린다. 되살리기는 "삭제 후 재작성"(createFromPlan)
            // 전용 동작이라 여기서는 다루지 않고 막기만 한다 — 대상이 다른 memory 행이라 되살리면
            // 두 추억이 하나로 뒤섞인다.
            memoryMapper.findByPlanIdAndWriterId(newPlanId, userId)
                    .filter(other -> !other.getId().equals(memory.getId()))
                    .ifPresent(other -> { throw new DomainException(ErrorCode.MEMORY_ALREADY_WRITTEN); });
        }

        memoryMapper.updateMemoryPlanId(memory.getId(), newPlanId);

        if (oldPlanId != null) {
            revertPlanStatusIfEmpty(oldPlanId);
        }
        if (newPlanId != null) {
            memoryMapper.updatePlanMemoryStatusWritten(newPlanId);
        }
    }

    @Transactional
    public CommentResponse createComment(long memoryId, long userId, CreateCommentRequest request) {
        Memory memory = findExisting(memoryId);
        roomService.assertActiveMember(memory.getRoomId(), userId);
        // 한 추억당 작성자 1인 1개(계약 §10, 2026-07-26 리더 결정). 고쳐 쓰려면 PATCH,
        // 지웠으면 다시 쓸 수 있다. DB의 UNIQUE(memory_id, writer_id)가 최종 방어선.
        if (commentMapper.findByMemoryIdAndWriterId(memoryId, userId).isPresent()) {
            throw new DomainException(ErrorCode.COMMENT_ALREADY_EXISTS);
        }

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
    public CommentResponse updateComment(long commentId, long userId, UpdateCommentRequest request) {
        MemoryComment comment = commentMapper.findById(commentId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (comment.getWriterId() != userId) {
            throw new DomainException(ErrorCode.NOT_WRITER);
        }
        commentMapper.update(commentId, request.content());
        return CommentResponse.from(commentMapper.findById(commentId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
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
