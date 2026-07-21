package com.korit.clovapi.domain.memory.dto;

import com.korit.clovapi.domain.memory.entity.MemoryComment;
import com.korit.clovapi.global.dto.UserSummaryResponse;

import java.time.LocalDateTime;

public record CommentResponse(String id, UserSummaryResponse writer, String content, LocalDateTime createdAt) {
    public static CommentResponse from(MemoryComment comment) {
        return new CommentResponse(
                String.valueOf(comment.getId()),
                new UserSummaryResponse(String.valueOf(comment.getWriterId()), comment.getWriterNickname(),
                        comment.getWriterProfileImageUrl()),
                comment.getContent(), comment.getCreatedAt());
    }
}
