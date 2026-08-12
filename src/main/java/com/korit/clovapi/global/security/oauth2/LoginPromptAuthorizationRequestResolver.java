package com.korit.clovapi.global.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * 로그아웃 후 다시 로그인 버튼을 누르면, 제공자가 "이미 로그인된 계정"으로 조용히
 * 통과시키지 않고 재인증 화면을 다시 띄우게 한다({@code prompt=login}). 우리 앱과의
 * 연결(동의)은 그대로 두고 인증만 다시 받는 것 — 회원가입 때의 동의 화면과는 다르다
 * ({@code prompt=consent}가 아니다).
 *
 * ⚠️ 네이버는 이 파라미터를 공식 지원하지 않는다(문서화된 대응 파라미터 없음). 값을
 * 그대로 보내되, 효과가 없을 수 있다는 걸 알고 있어야 한다 — 무해하므로(모르는 쿼리
 * 파라미터는 무시되는 게 OAuth 서버의 일반적 동작) 굳이 provider별로 분기하지 않는다.
 */
@Component
public class LoginPromptAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String PROMPT_PARAM = "prompt";
    private static final String PROMPT_LOGIN = "login";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public LoginPromptAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return withLoginPrompt(delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return withLoginPrompt(delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest withLoginPrompt(OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) {
            return null;
        }
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .additionalParameters(params -> params.put(PROMPT_PARAM, PROMPT_LOGIN))
                .build();
    }
}
