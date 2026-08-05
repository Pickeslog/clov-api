package com.korit.clovapi.domain.letter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.korit.clovapi.global.dto.UserSummaryResponse;

import java.time.LocalDateTime;

// title은 선택 입력이라 미입력 편지가 대부분이다 — ApiResponse/ApiError와 같은 패턴으로
// null이면 응답에서 필드 자체를 생략한다(계약 §행운편지, 이슈 #352).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LetterResponse(
        String id,
        UserSummaryResponse sender,
        UserSummaryResponse receiver,
        String title,
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
                row.getTitle(),
                row.getContent(),
                row.getEmoji(),
                row.getReadAt(),
                row.isFavorite(),
                row.getSentAt()
        );
    }
}
