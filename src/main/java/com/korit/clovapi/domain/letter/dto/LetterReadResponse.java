package com.korit.clovapi.domain.letter.dto;

import java.time.LocalDateTime;

public record LetterReadResponse(
        LocalDateTime readAt
) {
}
