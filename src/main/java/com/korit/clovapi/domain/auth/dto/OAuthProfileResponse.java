package com.korit.clovapi.domain.auth.dto;

import com.korit.clovapi.domain.auth.oauth.OAuthProfile;

public record OAuthProfileResponse(String email, String nickname, String provider) {
    public static OAuthProfileResponse from(OAuthProfile profile) {
        return new OAuthProfileResponse(profile.email(), profile.nickname(), profile.provider());
    }
}
