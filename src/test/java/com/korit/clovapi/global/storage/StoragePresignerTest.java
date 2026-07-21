package com.korit.clovapi.global.storage;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StoragePresigner 단위 테스트 — SigV4 서명은 로컬 계산이라 네트워크/Docker 없이 URL 형태를 검증한다.
 */
class StoragePresignerTest {

    private final StorageProperties props = new StorageProperties(
            "https://test-account.r2.cloudflarestorage.com",
            "auto",
            "clov-media",
            "test-access-key-id",
            "test-secret-access-key",
            "https://pub-testhash.r2.dev/"
    );

    @Test
    void presignPutBuildsSignedUrlAndPublicImageUrl() {
        try (StoragePresigner presigner = new StoragePresigner(props)) {
            StoragePresigner.PresignResult result =
                    presigner.presignPut("rooms/1/plans/2/proposal-abc.jpg", "image/jpeg");

            String uploadUrl = result.uploadUrl();
            assertTrue(uploadUrl.startsWith("https://"), uploadUrl);
            // path-style: 서명 PUT URL에 endpoint·bucket·key가 포함되고 SigV4 서명 쿼리가 붙는다.
            assertTrue(uploadUrl.contains("test-account.r2.cloudflarestorage.com"), uploadUrl);
            assertTrue(uploadUrl.contains("clov-media/rooms/1/plans/2/proposal-abc.jpg"), uploadUrl);
            assertTrue(uploadUrl.contains("X-Amz-Signature"), uploadUrl);
            assertTrue(uploadUrl.contains("X-Amz-Expires=300"), uploadUrl);
            // 브라우저 PUT은 Content-Type 외 헤더를 못 맞춘다. AWS SDK v2 2.30+가 기본으로
            // 끼워 넣는 flexible-checksum 서명(x-amz-sdk-checksum-algorithm 등)이 URL에 있으면
            // R2로의 presigned PUT이 브라우저에서 깨진다 → 서명에 checksum이 없어야 한다.
            assertFalse(uploadUrl.toLowerCase(Locale.ROOT).contains("checksum"),
                    "presigned URL이 checksum 헤더를 요구하면 브라우저 PUT이 깨진다: " + uploadUrl);
            // 공개 조회 URL은 public-base-url 기준(끝 슬래시 중복 없이).
            assertEquals("https://pub-testhash.r2.dev/rooms/1/plans/2/proposal-abc.jpg", result.imageUrl());
            assertEquals(300, result.expiresIn());
        }
    }

    @Test
    void publicUrlFallsBackToEndpointBucketWhenNoPublicBase() {
        StorageProperties noPublicBase = new StorageProperties(
                "https://test-account.r2.cloudflarestorage.com", "auto", "clov-media",
                "test-access-key-id", "test-secret-access-key", null);
        try (StoragePresigner presigner = new StoragePresigner(noPublicBase)) {
            StoragePresigner.PresignResult result =
                    presigner.presignPut("memories/9/x.png", "image/png");

            assertEquals("https://test-account.r2.cloudflarestorage.com/clov-media/memories/9/x.png",
                    result.imageUrl());
        }
    }
}
