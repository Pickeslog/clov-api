package com.korit.clovapi.domain.memory.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 이미지 업로드 커밋. {@code sortOrder}가 없으면 서버가 마지막 순서로 덧붙인다.
 * {@code imageUrl}은 presign의 imageUrl(공개 조회 URL).
 */
public record CommitImageRequest(
        @NotBlank String imageUrl,
        Integer sortOrder
) {
}
