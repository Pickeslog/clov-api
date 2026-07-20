package com.korit.clovapi.domain.auth.dto;

public record OAuthExchangeResponse(
        boolean authenticated,
        String accessToken,
        String refreshToken,
        UserResponse user,
        String registrationToken,
        OAuthProfileResponse profile
) {
    public static OAuthExchangeResponse authenticated(AuthResponse response) {
        return new OAuthExchangeResponse(true, response.accessToken(), response.refreshToken(), response.user(), null, null);
    }

    public static OAuthExchangeResponse registration(String token, OAuthProfileResponse profile) {
        return new OAuthExchangeResponse(false, null, null, null, token, profile);
    }
}
