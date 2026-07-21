package com.korit.clovapi.domain.letter.dto;

import jakarta.validation.constraints.NotBlank;

public record LetterSendRequest(
        String receiverUserId,
        Boolean broadcast,
        @NotBlank String content,
        String emoji
) {
}
