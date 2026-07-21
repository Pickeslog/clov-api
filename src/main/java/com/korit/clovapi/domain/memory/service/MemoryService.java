package com.korit.clovapi.domain.memory.service;

import com.korit.clovapi.domain.memory.dto.CommentResponse;
import com.korit.clovapi.domain.memory.dto.CommentsResponse;
import com.korit.clovapi.domain.memory.dto.CreateCommentRequest;
import com.korit.clovapi.domain.memory.dto.CreateMemoryRequest;
import com.korit.clovapi.domain.memory.dto.MemoryDetailResponse;
import com.korit.clovapi.domain.memory.dto.MemoryFeedResponse;
import com.korit.clovapi.domain.memory.dto.MemorySummaryResponse;
import com.korit.clovapi.domain.memory.dto.UpdateMemoryRequest;
import com.korit.clovapi.domain.memory.entity.Memory;
import com.korit.clovapi.domain.memory.entity.MemoryComment;
import com.korit.clovapi.domain.memory.mapper.CommentMapper;
import com.korit.clovapi.domain.memory.mapper.MemoryMapper;
import com.korit.clovapi.domain.memory.mapper.ParticipantRow;
import com.korit.clovapi.domain.room.service.RoomService;
import com.korit.clovapi.global.dto.UserSummaryResponse;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class MemoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final MemoryMapper memoryMapper;
    private final RoomService roomService;
    private final CommentMapper commentMapper;

    public MemoryService(MemoryMapper memoryMapper, RoomService roomService, CommentMapper commentMapper) {
        this.memoryMapper = memoryMapper;
        this.roomService = roomService;
        this.commentMapper = commentMapper;
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
        List<String> tags = memoryMapper.findTags(memoryId);
        List<UserSummaryResponse> participants = memoryMapper.findParticipants(memoryId).stream()
                .map(this::toUserSummary)
                .toList();
        return MemoryDetailResponse.from(memory, tags, participants);
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
