package com.korit.clovapi.domain.auth.service;

import com.korit.clovapi.domain.auth.dto.OAuthConsentRequest;
import com.korit.clovapi.domain.auth.dto.OAuthExchangeResponse;
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
                .orElseGet(() -> OAuthExchangeResponse.registration(
                        codeStore.issue(profile),
                        OAuthProfileResponse.from(profile)
                ));
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
}
