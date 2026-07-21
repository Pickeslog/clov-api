package com.korit.clovapi.domain.letter.dto;

import java.time.LocalDateTime;

public record LetterResponse(
        String id,
        UserSummaryResponse sender,
        UserSummaryResponse receiver,
        String content,
        String emoji,
        LocalDateTime readAt,
        boolean isFavorite,
        LocalDateTime sentAt
) {
    public static LetterResponse from(LetterDetailRow row) {
        return new LetterResponse(
                String.valueOf(row.getId()),
                new UserSummaryResponse(
                        String.valueOf(row.getSenderId()),
                        row.getSenderNickname(),
                        row.getSenderProfileImageUrl()
                ),
                new UserSummaryResponse(
                        String.valueOf(row.getReceiverId()),
                        row.getReceiverNickname(),
                        row.getReceiverProfileImageUrl()
                ),
                row.getContent(),
                row.getEmoji(),
                row.getReadAt(),
                row.isFavorite(),
                row.getSentAt()
        );
    }
}
