package com.korit.clovapi.global.security.jwt;

import java.time.Instant;

public record JwtClaims(
        long userId,
        TokenType tokenType,
        Instant expiresAt
) {
}
