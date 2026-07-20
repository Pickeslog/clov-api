package com.korit.clovapi.domain.auth.oauth;

public record OAuthProfile(String provider, String subject, String email, String nickname) {
}
