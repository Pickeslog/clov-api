package com.korit.clovapi.domain.memory.dto;

import com.korit.clovapi.domain.memory.entity.Memory;
import com.korit.clovapi.global.dto.UserSummaryResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MemoryDetailResponse(
        String id, String roomId, String planId, UserSummaryResponse writer,
        String title, String content, LocalDate memoryDate,
        List<MemoryImageResponse> images, List<String> tags, List<UserSummaryResponse> participants,
        int commentCount, LocalDateTime createdAt
) {
    public static MemoryDetailResponse from(Memory memory, List<MemoryImageResponse> images, List<String> tags,
                                            List<UserSummaryResponse> participants) {
        return new MemoryDetailResponse(
                String.valueOf(memory.getId()),
                String.valueOf(memory.getRoomId()),
                memory.getPlanId() == null ? null : String.valueOf(memory.getPlanId()),
                new UserSummaryResponse(String.valueOf(memory.getWriterId()), memory.getWriterNickname(),
                        memory.getWriterProfileImageUrl()),
                memory.getTitle(), memory.getContent(), memory.getMemoryDate(),
                images, tags, participants,
                memory.getCommentCount() == null ? 0 : memory.getCommentCount(), memory.getCreatedAt());
    }
}
