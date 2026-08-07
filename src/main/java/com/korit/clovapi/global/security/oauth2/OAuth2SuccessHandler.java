package com.korit.clovapi.global.security.oauth2;

import com.korit.clovapi.domain.auth.oauth.OAuthOneTimeCodeStore;
import com.korit.clovapi.domain.auth.oauth.OAuthProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String FRONTEND_REDIRECT_PATH = "/oauth2/redirect";

    private final OAuthOneTimeCodeStore codeStore;

    public OAuth2SuccessHandler(OAuthOneTimeCodeStore codeStore) {
        this.codeStore = codeStore;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws java.io.IOException, jakarta.servlet.ServletException {
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        OAuthProfile profile = (OAuthProfile) user.getAttributes().get("oauthProfile");
        String code = codeStore.issue(profile);
        // 요청이 들어온 도메인(scheme+host) 기준으로 계산한다 — clovlabcalss.store·clovlov.xyz 등
        // 어느 도메인에서 로그인을 시작했든 그 도메인으로 그대로 돌아간다(#147).
        // forward-headers-strategy: framework 덕에 nginx의 X-Forwarded-* 를 반영한 값이다.
        String redirectUrl = ServletUriComponentsBuilder.fromContextPath(request)
                .path(FRONTEND_REDIRECT_PATH)
                .queryParam("code", code)
                .build()
                .toUriString();
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
