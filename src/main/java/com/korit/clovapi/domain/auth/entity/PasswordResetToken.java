package com.korit.clovapi.domain.auth.entity;

import java.time.LocalDateTime;

/**
 * 비밀번호 재설정 토큰(계약 §4-4).
 *
 * <p>{@code refresh_tokens}와 같이 <b>원문이 아닌 해시</b>를 저장한다. 원문은 메일 링크에만 실린다.
 * 무효화 사유가 둘이라 컬럼을 나눴다 — 사용자가 실제로 재설정을 마친 것({@code usedAt})과
 * 같은 계정이 재요청해 자동 폐기된 것({@code revokedAt})은 운영상 의미가 다르다.
 * 유효 판정은 {@code usedAt IS NULL AND revokedAt IS NULL AND expiresAt > now}.
 */
public class PasswordResetToken {

    private Long id;
    private Long userId;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
