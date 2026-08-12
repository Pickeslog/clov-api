package com.korit.clovapi.global.security.oauth2;

import com.korit.clovapi.domain.auth.oauth.OAuthOneTimeCodeStore;
import com.korit.clovapi.domain.auth.oauth.OAuthProfile;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuth2SuccessHandlerTest {

    private final OAuthOneTimeCodeStore codeStore = new OAuthOneTimeCodeStore();
    // 빈 문자열 = 운영 기본값(application.yaml 공통 블록과 동일) — request-relative 계산을 탄다.
    private final OAuth2SuccessHandler handler = new OAuth2SuccessHandler(codeStore, "");

    @Test
    void redirectsBackToClovlovXyzWhenTheRequestArrivedThere() throws Exception {
        assertRedirectsToTheArrivalDomain("clovlov.xyz");
    }

    @Test
    void redirectsBackToClovlabcalssStoreWhenTheRequestArrivedThere() throws Exception {
        assertRedirectsToTheArrivalDomain("clovlabcalss.store");
    }

    // #155 — 로컬은 프론트·백엔드 포트가 분리돼 있어 request-relative 계산이 백엔드 자기
    // 자신으로 돌아가버린다. local-frontend-origin이 채워져 있으면 요청 도메인과 무관하게
    // 그 고정 오리진으로 돌아가야 한다.
    @Test
    void redirectsToTheFixedLocalOriginRegardlessOfTheRequestDomainWhenConfigured() throws Exception {
        OAuth2SuccessHandler localHandler = new OAuth2SuccessHandler(codeStore, "http://localhost:5173");
        MockHttpServletRequest request = requestArrivingOn("localhost", 8080, "http");
        MockHttpServletResponse response = new MockHttpServletResponse();

        localHandler.onAuthenticationSuccess(request, response, authenticationFor(profile()));

        URI redirect = URI.create(response.getRedirectedUrl());
        assertEquals("http", redirect.getScheme());
        assertEquals("localhost", redirect.getHost());
        assertEquals(5173, redirect.getPort());
        assertEquals("/oauth2/redirect", redirect.getPath());
        assertTrue(redirect.getQuery().startsWith("code="));
    }

    private void assertRedirectsToTheArrivalDomain(String domain) throws Exception {
        MockHttpServletRequest request = requestArrivingOn(domain, 443, "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authenticationFor(profile()));

        URI redirect = URI.create(response.getRedirectedUrl());
        assertEquals("https", redirect.getScheme());
        assertEquals(domain, redirect.getHost());
        assertEquals("/oauth2/redirect", redirect.getPath());
        assertTrue(redirect.getQuery().startsWith("code="));
    }

    // ForwardedHeaderFilter가 X-Forwarded-*를 이미 반영한 뒤의 요청 상태를 흉내낸다 —
    // 그 필터 자체의 동작은 스프링 프레임워크의 책임이라 여기서 다시 검증하지 않는다.
    private static MockHttpServletRequest requestArrivingOn(String domain, int port, String scheme) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
        request.setScheme(scheme);
        request.setServerName(domain);
        request.setServerPort(port);
        return request;
    }

    private static OAuthProfile profile() {
        return new OAuthProfile("kakao", "kakao-subject", "test@example.test", "테스트");
    }

    private static Authentication authenticationFor(OAuthProfile profile) {
        OAuth2User oAuth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", profile.subject(), "oauthProfile", profile),
                "sub"
        );
        return new TestingAuthenticationToken(oAuth2User, null, "ROLE_USER");
    }
}
