package com.korit.clovapi.domain.memory.dto;

import com.korit.clovapi.domain.memory.entity.Memory;
import com.korit.clovapi.global.dto.UserSummaryResponse;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public record MemorySummaryResponse(
        String id, String planId, String title, String content, LocalDate memoryDate,
        String thumbnailUrl, int imageCount,
        UserSummaryResponse writer, List<UserSummaryResponse> participants,
        List<String> tags, int commentCount
) {
    public static MemorySummaryResponse from(Memory memory) {
        return from(memory, null, 0, List.of());
    }

    /**
     * 피드 카드용 요약. thumbnailUrl = 대표 이미지 공개 URL(없으면 null → 프론트 클로버 fallback),
     * imageCount = 총 이미지 수, participants = 참여 멤버, content = 본문(카드 미리보기용).
     */
    public static MemorySummaryResponse from(Memory memory, String thumbnailUrl, int imageCount,
                                             List<UserSummaryResponse> participants) {
        List<String> tags = memory.getTagsCsv() == null || memory.getTagsCsv().isBlank()
                ? List.of()
                : Arrays.asList(memory.getTagsCsv().split(","));
        return new MemorySummaryResponse(
                String.valueOf(memory.getId()),
                memory.getPlanId() == null ? null : String.valueOf(memory.getPlanId()),
                memory.getTitle(), memory.getContent(), memory.getMemoryDate(),
                thumbnailUrl, imageCount,
                new UserSummaryResponse(String.valueOf(memory.getWriterId()), memory.getWriterNickname(),
                        memory.getWriterProfileImageUrl()),
                participants == null ? List.of() : participants,
                tags, memory.getCommentCount() == null ? 0 : memory.getCommentCount());
    }
}
