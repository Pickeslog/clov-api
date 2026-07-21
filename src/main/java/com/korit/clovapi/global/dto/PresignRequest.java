package com.korit.clovapi.global.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 이미지 업로드 presign 공통 요청(계약 §4-3). {@code fileSize}는 쿼터 검증용(선택).
 * plan 단계사진은 stage가 필요해 별도 요청 타입을 쓴다.
 */
public record PresignRequest(
        @NotBlank String contentType,
        Long fileSize
) {
}
