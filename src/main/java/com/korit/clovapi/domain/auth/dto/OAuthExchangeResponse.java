package com.korit.clovapi.domain.auth.dto;

public record OAuthExchangeResponse(
        boolean authenticated,
        String accessToken,
        String refreshToken,
        UserResponse user,
        String registrationToken,
        OAuthProfileResponse profile,
        // (C) 이메일 매칭 계정 연결 후보(§4-2, web-design-repository#90) — (B)와 구분되는 상태.
        boolean linkCandidate,
        String maskedEmail
) {
    public static OAuthExchangeResponse authenticated(AuthResponse response) {
        return new OAuthExchangeResponse(true, response.accessToken(), response.refreshToken(), response.user(), null, null, false, null);
    }

    public static OAuthExchangeResponse registration(String token, OAuthProfileResponse profile) {
        return new OAuthExchangeResponse(false, null, null, null, token, profile, false, null);
    }

    public static OAuthExchangeResponse linkCandidate(String token, String maskedEmail) {
        return new OAuthExchangeResponse(false, null, null, null, token, null, true, maskedEmail);
    }
}
