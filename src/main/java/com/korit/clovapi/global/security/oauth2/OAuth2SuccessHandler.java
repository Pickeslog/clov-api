package com.korit.clovapi.global.security.oauth2;

import com.korit.clovapi.domain.auth.oauth.OAuthOneTimeCodeStore;
import com.korit.clovapi.domain.auth.oauth.OAuthProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String FRONTEND_REDIRECT_PATH = "/oauth2/redirect";

    private final OAuthOneTimeCodeStore codeStore;

    // 로컬 전용 — 운영은 빈 문자열이라 이 필드는 쓰이지 않는다(app.oauth.local-frontend-origin,
    // dev 프로필에서만 http://localhost:5173로 채워진다). 로컬은 프론트(5173)·백엔드(8080)가
    // nginx 없이 다른 포트라 아래 request-relative 계산이 백엔드 자기 자신으로 돌아가버린다(#155).
    private final String localFrontendOrigin;

    public OAuth2SuccessHandler(OAuthOneTimeCodeStore codeStore,
                                @Value("${app.oauth.local-frontend-origin:}") String localFrontendOrigin) {
        this.codeStore = codeStore;
        this.localFrontendOrigin = localFrontendOrigin;
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
        String redirectUrl = localFrontendOrigin.isBlank()
                // 운영 — 요청이 들어온 도메인(scheme+host) 기준으로 계산한다. clovlabcalss.store·
                // clovlov.xyz 등 어느 도메인에서 로그인을 시작했든 그 도메인으로 그대로 돌아간다(#147).
                // forward-headers-strategy: framework 덕에 nginx의 X-Forwarded-* 를 반영한 값이다.
                ? ServletUriComponentsBuilder.fromContextPath(request)
                        .path(FRONTEND_REDIRECT_PATH)
                        .queryParam("code", code)
                        .build()
                        .toUriString()
                // 로컬 — 고정된 프론트 오리진으로 돌아간다. 여기서 request 기준으로 계산하면
                // 백엔드 포트(8080)로 돌아가 프론트 라우트를 못 찾는다(#155).
                : UriComponentsBuilder.fromUriString(localFrontendOrigin)
                        .path(FRONTEND_REDIRECT_PATH)
                        .queryParam("code", code)
                        .build()
                        .toUriString();
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
