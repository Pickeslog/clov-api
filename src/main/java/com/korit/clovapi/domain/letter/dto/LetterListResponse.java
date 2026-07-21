package com.korit.clovapi.domain.letter.dto;

import java.util.List;

public record LetterListResponse(
        List<LetterResponse> items,
        int page,
        int size,
        long total
) {
    public static LetterListResponse of(List<LetterResponse> items) {
        return new LetterListResponse(items, 0, items.size(), items.size());
    }
}
