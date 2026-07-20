package com.korit.clovapi.global.security.jwt;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(new JwtProperties("clov-api-test", SECRET));

    @Test
    void createsAndParsesAccessTokens() {
        JwtClaims claims = jwtTokenProvider.parse(jwtTokenProvider.createAccessToken(42L));

        assertEquals(42L, claims.userId());
        assertEquals(TokenType.ACCESS, claims.tokenType());
        assertTrue(claims.expiresAt().isAfter(Instant.now().plus(Duration.ofMinutes(29))));
        assertTrue(claims.expiresAt().isBefore(Instant.now().plus(Duration.ofMinutes(31))));
    }

    @Test
    void createsAndParsesRefreshTokens() {
        JwtClaims claims = jwtTokenProvider.parse(jwtTokenProvider.createRefreshToken(42L));

        assertEquals(42L, claims.userId());
        assertEquals(TokenType.REFRESH, claims.tokenType());
        assertTrue(claims.expiresAt().isAfter(Instant.now().plus(Duration.ofDays(13))));
    }
}
