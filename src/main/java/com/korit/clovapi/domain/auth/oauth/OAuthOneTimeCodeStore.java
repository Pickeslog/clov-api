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

    // 로그인 리다이렉트 코드는 브라우저가 자동으로 소모한다(사람 개입 없음) — 짧아도 된다.
    private static final Duration LOGIN_CODE_TTL = Duration.ofSeconds(60);
    // 등록토큰은 신규 가입자가 약관 두 개를 읽고 체크한 뒤 눌러야 소모된다 — 로그인 코드와
    // 같은 60초를 썼더니 그 사이에 만료돼 "인증 시간이 만료되었어요"로 잘못 떨어졌다(#247).
    private static final Duration REGISTRATION_TOKEN_TTL = Duration.ofMinutes(10);
    private final ConcurrentHashMap<String, StoredProfile> codes = new ConcurrentHashMap<>();

    public String issue(OAuthProfile profile) {
        return issue(profile, LOGIN_CODE_TTL);
    }

    public String issueForRegistration(OAuthProfile profile) {
        return issue(profile, REGISTRATION_TOKEN_TTL);
    }

    private String issue(OAuthProfile profile, Duration ttl) {
        String code = UUID.randomUUID().toString();
        codes.put(code, new StoredProfile(profile, Instant.now().plus(ttl)));
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
