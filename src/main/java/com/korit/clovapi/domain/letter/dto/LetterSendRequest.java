package com.korit.clovapi.domain.letter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LetterSendRequest(
        String receiverUserId,
        Boolean broadcast,
        // 선택 입력 — 미입력 시 null(계약 §행운편지, 이슈 #352).
        @Size(max = 60) String title,
        @NotBlank String content,
        String emoji
) {
}
