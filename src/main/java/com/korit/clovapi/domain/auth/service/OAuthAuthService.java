package com.korit.clovapi.domain.auth.service;

import com.korit.clovapi.domain.auth.dto.OAuthConsentRequest;
import com.korit.clovapi.domain.auth.dto.OAuthExchangeResponse;
import com.korit.clovapi.domain.auth.dto.OAuthLinkConfirmRequest;
import com.korit.clovapi.domain.auth.dto.OAuthProfileResponse;
import com.korit.clovapi.domain.auth.entity.User;
import com.korit.clovapi.domain.auth.mapper.UserMapper;
import com.korit.clovapi.domain.auth.oauth.OAuthOneTimeCodeStore;
import com.korit.clovapi.domain.auth.oauth.OAuthProfile;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class OAuthAuthService {

    private final OAuthOneTimeCodeStore codeStore;
    private final UserMapper userMapper;
    private final AuthService authService;

    public OAuthAuthService(OAuthOneTimeCodeStore codeStore, UserMapper userMapper, AuthService authService) {
        this.codeStore = codeStore;
        this.userMapper = userMapper;
        this.authService = authService;
    }

    public OAuthExchangeResponse exchange(String code) {
        OAuthProfile profile = codeStore.consume(code);
        return userMapper.findByOauth(profile.provider(), profile.subject())
                .map(user -> OAuthExchangeResponse.authenticated(authService.authenticate(user)))
                .orElseGet(() -> userMapper.findByEmail(profile.email())
                        // (C) provider/subject는 안 겹치지만 같은 이메일의 기존 계정이 있다 —
                        // 완전 신규(B)로 보내지 않고 연결 확인을 한 번 거친다(§4-2, web-design-repository#90).
                        .<OAuthExchangeResponse>map(existing -> OAuthExchangeResponse.linkCandidate(
                                codeStore.issueForRegistration(profile),
                                maskEmail(profile.email())
                        ))
                        .orElseGet(() -> OAuthExchangeResponse.registration(
                                codeStore.issueForRegistration(profile),
                                OAuthProfileResponse.from(profile)
                        )));
    }

    public com.korit.clovapi.domain.auth.dto.AuthResponse consent(OAuthConsentRequest request) {
        if (!request.agreements().service() || !request.agreements().privacy()) {
            throw new DomainException(ErrorCode.TERMS_REQUIRED);
        }
        OAuthProfile profile = codeStore.consume(request.registrationToken());
        return userMapper.findByOauth(profile.provider(), profile.subject())
                .map(authService::authenticate)
                .orElseGet(() -> authService.signupOAuth(profile, request.agreements()));
    }

    /**
     * (C) 연결 확인(§4-2, web-design-repository#90) — 사용자가 "이 계정으로 연결" 확인 후
     * 기존 계정으로 로그인시킨다. 새 users row는 만들지 않고, oauth_provider/oauth_subject도
     * 최초 가입 provider 값 그대로 둔다 — 다음에 이 provider로 다시 오면 매번 이 경로를 탄다.
     */
    public com.korit.clovapi.domain.auth.dto.AuthResponse linkConfirm(OAuthLinkConfirmRequest request) {
        OAuthProfile profile = codeStore.consume(request.registrationToken());
        User user = userMapper.findByEmail(profile.email())
                .orElseThrow(() -> new DomainException(ErrorCode.OAUTH_CODE_INVALID));
        return authService.authenticate(user);
    }

    // "k***@gmail.com" 형태. 로컬파트 첫 글자만 남기고 나머지는 가린다 — 다른 사람 이메일을
    // 그대로 노출하지 않으면서 "이거 내 이메일 맞나" 확인은 되게.
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "*".repeat(Math.max(at, 1)) + email.substring(at);
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
