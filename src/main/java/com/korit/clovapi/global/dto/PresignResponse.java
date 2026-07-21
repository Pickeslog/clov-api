package com.korit.clovapi.global.dto;

import com.korit.clovapi.global.storage.StoragePresigner;

/**
 * 이미지 업로드 presign 공통 응답(계약 §4-3): 서명 PUT URL·공개 조회 URL·만료(초).
 */
public record PresignResponse(
        String uploadUrl,
        String imageUrl,
        Integer expiresIn
) {
    public static PresignResponse from(StoragePresigner.PresignResult result) {
        return new PresignResponse(result.uploadUrl(), result.imageUrl(), result.expiresIn());
    }
}
