package com.korit.clovapi.domain.letter.dto;

public record UserSummaryResponse(
        String id,
        String nickname,
        String profileImageUrl
) {
}
