package com.korit.clovapi.domain.memory.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** {@code imageIds} 순서대로 sort_order를 재부여한다. */
public record ReorderImagesRequest(
        @NotEmpty List<String> imageIds
) {
}
