package com.korit.clovapi.domain.auth.dto;

/**
 * 재설정 화면 진입 시 토큰 유효성(계약 §4-4).
 * 무효하면 400 {@code PASSWORD_RESET_TOKEN_INVALID}이므로 이 응답의 {@code valid}는 항상 true다.
 * 그래도 필드를 두는 것은 계약에 명시된 형태이고, 프론트가 상태코드가 아니라 본문으로도 판정할 수 있게 하기 위해서다.
 */
public record PasswordResetTokenStatusResponse(boolean valid) {
}
