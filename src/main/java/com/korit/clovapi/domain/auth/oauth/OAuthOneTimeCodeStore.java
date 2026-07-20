package com.korit.clovapi.domain.auth.oauth;

import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuthOneTimeCodeStore {

    private static final Duration TTL = Duration.ofSeconds(60);
    private final ConcurrentHashMap<String, StoredProfile> codes = new ConcurrentHashMap<>();

    public String issue(OAuthProfile profile) {
        String code = UUID.randomUUID().toString();
        codes.put(code, new StoredProfile(profile, Instant.now().plus(TTL)));
        return code;
    }

    public OAuthProfile consume(String code) {
        StoredProfile stored = codes.remove(code);
        if (stored == null || !stored.expiresAt().isAfter(Instant.now())) {
            throw new DomainException(ErrorCode.OAUTH_CODE_INVALID);
        }
        return stored.profile();
    }

    // Production must replace this local store with a shared, TTL-backed store.
    private record StoredProfile(OAuthProfile profile, Instant expiresAt) {
    }
}
