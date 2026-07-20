package com.korit.clovapi.domain.auth.dto;

public record TokenResponse(String accessToken, String refreshToken) {
}
