package com.korit.clovapi.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 오브젝트 스토리지(Cloudflare R2, S3 호환) 설정. 값은 로컬 전용 secret(application-secret.yaml)에서 주입한다.
 * provider별 분기 없이 {@code endpoint} 유무만으로 AWS/R2/MinIO를 공통 처리한다.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        String publicBaseUrl
) {
}
