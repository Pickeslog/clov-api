package com.korit.clovapi.domain.memory.dto;

import com.korit.clovapi.domain.memory.entity.Memory;
import com.korit.clovapi.global.dto.UserSummaryResponse;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public record MemorySummaryResponse(
        String id, String planId, String title, LocalDate memoryDate, String thumbnailUrl,
        UserSummaryResponse writer, List<String> tags, int commentCount
) {
    public static MemorySummaryResponse from(Memory memory) {
        List<String> tags = memory.getTagsCsv() == null || memory.getTagsCsv().isBlank()
                ? List.of()
                : Arrays.asList(memory.getTagsCsv().split(","));
        return new MemorySummaryResponse(
                String.valueOf(memory.getId()),
                memory.getPlanId() == null ? null : String.valueOf(memory.getPlanId()),
                memory.getTitle(), memory.getMemoryDate(), null,
                new UserSummaryResponse(String.valueOf(memory.getWriterId()), memory.getWriterNickname(),
                        memory.getWriterProfileImageUrl()),
                tags, memory.getCommentCount() == null ? 0 : memory.getCommentCount());
    }
}
