package com.korit.clovapi.global.storage;

import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

/**
 * 오브젝트 스토리지 업로드용 presigned PUT URL 발급 공용 유틸.
 *
 * <p>서명은 로컬 계산이라 발급 시 네트워크가 필요 없다(실제 업로드만 버킷에 도달).
 * {@code endpoint}가 있으면 {@code endpointOverride} + path-style로 R2/MinIO를 처리하고,
 * 없으면 표준 AWS S3로 동작한다.
 */
public class StoragePresigner implements AutoCloseable {

    private static final Duration TTL = Duration.ofSeconds(300);

    private final StorageProperties props;
    private final S3Presigner presigner;

    public StoragePresigner(StorageProperties props) {
        this.props = props;
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                // R2/MinIO는 버킷 서브도메인 대신 path-style을 요구한다.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        if (StringUtils.hasText(props.endpoint())) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        this.presigner = builder.build();
    }

    /**
     * 주어진 objectKey에 대한 서명 PUT URL을 발급한다.
     *
     * @param objectKey 버킷 내 고유 경로(도메인별 규칙으로 호출부에서 생성)
     * @param contentType 업로드할 파일의 MIME 타입(서명에 포함)
     * @return {@link PresignResult} — 서명 PUT URL·공개 조회 URL·만료(초)
     */
    public PresignResult presignPut(String objectKey, String contentType) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(TTL)
                .putObjectRequest(objectRequest)
                .build();
        String uploadUrl = presigner.presignPutObject(presignRequest).url().toString();
        return new PresignResult(uploadUrl, publicUrl(objectKey), (int) TTL.getSeconds());
    }

    /** 공개 조회 URL. {@code public-base-url}가 있으면 그 기준, 없으면 endpoint/bucket로 구성. */
    private String publicUrl(String objectKey) {
        String base = props.publicBaseUrl();
        if (!StringUtils.hasText(base)) {
            base = trimSlash(props.endpoint()) + "/" + props.bucket();
        }
        return trimSlash(base) + "/" + objectKey;
    }

    private static String trimSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @Override
    public void close() {
        presigner.close();
    }

    public record PresignResult(String uploadUrl, String imageUrl, int expiresIn) {
    }
}
