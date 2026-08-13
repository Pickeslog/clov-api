package com.korit.clovapi.domain.auth;

import com.jayway.jsonpath.JsonPath;
import com.korit.clovapi.domain.auth.oauth.OAuthOneTimeCodeStore;
import com.korit.clovapi.domain.auth.oauth.OAuthProfile;
import com.korit.clovapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OAuthIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuthOneTimeCodeStore codeStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String email;
    private OAuthProfile profile;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        email = "oauth-it-" + suffix + "@example.test";
        profile = new OAuthProfile("google", "google-" + suffix, email, "OAuth Test");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.query("SELECT id FROM users WHERE email = ?", (resultSet, rowNum) -> resultSet.getLong("id"), email)
                .forEach(userId -> {
                    jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
                    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
                });
    }

    @Test
    void exchangesOneTimeCodeAndCreatesSocialUserAfterConsent() throws Exception {
        String oneTimeCode = codeStore.issue(profile);
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + oneTimeCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.data.profile.email").value(email))
                .andReturn();

        String registrationToken = JsonPath.read(
                registration.getResponse().getContentAsString(),
                "$.data.registrationToken"
        );
        mockMvc.perform(post("/api/v1/auth/oauth/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"" + registrationToken + "\","
                                + "\"agreements\":{\"service\":false,\"privacy\":true,\"marketing\":false}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TERMS_REQUIRED"));

        mockMvc.perform(post("/api/v1/auth/oauth/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"" + registrationToken + "\","
                                + "\"agreements\":{\"service\":true,\"privacy\":true,\"marketing\":false}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.user.email").value(email));

        String existingCode = codeStore.issue(profile);
        mockMvc.perform(post("/api/v1/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + existingCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.registrationToken").doesNotExist());

        mockMvc.perform(post("/api/v1/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + existingCode + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OAUTH_CODE_INVALID"));
    }

    // web-design-repository#90 — provider는 다르지만 이메일이 같은 기존 계정이 있으면
    // (B) 신규가 아니라 (C) linkCandidate로 응답하고, link-confirm으로 확인해야 로그인된다.
    // 새 users row는 안 생기고 oauth_provider/oauth_subject는 최초 가입 값 그대로 남는다.
    @Test
    void linksToExistingAccountWhenEmailMatchesDifferentProvider() throws Exception {
        // 카카오로 먼저 가입.
        OAuthProfile kakaoProfile = new OAuthProfile("kakao", "kakao-" + UUID.randomUUID(), email, "Kakao Nick");
        String kakaoCode = codeStore.issue(kakaoProfile);
        MvcResult kakaoExchange = mockMvc.perform(post("/api/v1/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + kakaoCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andReturn();
        String kakaoRegToken = JsonPath.read(kakaoExchange.getResponse().getContentAsString(), "$.data.registrationToken");
        mockMvc.perform(post("/api/v1/auth/oauth/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"" + kakaoRegToken + "\","
                                + "\"agreements\":{\"service\":true,\"privacy\":true,\"marketing\":false}}"))
                .andExpect(status().isOk());

        // 같은 이메일로 네이버 로그인 시도 → (B) 신규가 아니라 (C) linkCandidate.
        OAuthProfile naverProfile = new OAuthProfile("naver", "naver-" + UUID.randomUUID(), email, "Naver Nick");
        String naverCode = codeStore.issue(naverProfile);
        MvcResult naverExchange = mockMvc.perform(post("/api/v1/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + naverCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.data.linkCandidate").value(true))
                .andExpect(jsonPath("$.data.maskedEmail").value(email.charAt(0) + "***" + email.substring(email.indexOf('@'))))
                .andExpect(jsonPath("$.data.profile").doesNotExist())
                .andReturn();
        String linkToken = JsonPath.read(naverExchange.getResponse().getContentAsString(), "$.data.registrationToken");

        // 연결 확인 → 기존(카카오) 계정으로 바로 로그인. 새 계정 생성 아님.
        mockMvc.perform(post("/api/v1/auth/oauth/link-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"" + linkToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.user.email").value(email));

        String provider = jdbcTemplate.queryForObject("SELECT oauth_provider FROM users WHERE email = ?", String.class, email);
        assertTrue("kakao".equals(provider), "연결 후에도 최초 가입 provider(kakao)를 유지해야 한다: " + provider);
        Long userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Long.class, email);
        assertTrue(userCount != null && userCount == 1L, "새 계정이 따로 생기면 안 된다");

        // 재사용된 토큰 → 400(OAUTH_CODE_INVALID는 실제로 BAD_REQUEST로 매핑돼 있다,
        // ErrorCode.java:52 — 위 exchange 재사용 케이스와 동일한 매핑).
        mockMvc.perform(post("/api/v1/auth/oauth/link-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"" + linkToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OAUTH_CODE_INVALID"));
    }

    @Test
    void startsEachConfiguredOAuthProvider() throws Exception {
        for (String provider : new String[]{"kakao", "naver", "google"}) {
            mockMvc.perform(get("/oauth2/authorization/" + provider))
                    .andExpect(status().is3xxRedirection());
        }
    }

    // #165 — 로그아웃 후 재로그인 시 제공자 세션을 그대로 타지 않고 재인증하도록
    // prompt=login을 인가 요청에 얹는다. 동적 redirect_uri(#147) 로직과 안 깨지는지도 확인.
    @Test
    void addsLoginPromptToEveryAuthorizationRequest() throws Exception {
        for (String provider : new String[]{"kakao", "naver", "google"}) {
            MvcResult result = mockMvc.perform(get("/oauth2/authorization/" + provider))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            String location = result.getResponse().getHeader("Location");
            assertTrue(location != null && location.contains("prompt=login"),
                    provider + " 인가 요청에 prompt=login이 있어야 한다: " + location);
        }
    }

    // {baseUrl} 플레이스홀더(application.yaml)가 요청 도메인마다 다른 redirect_uri를
    // provider에 보내는지 확인한다 — clovlabcalss.store·clovlov.xyz 병행 지원의 핵심(#147).
    @Test
    void sendsARedirectUriMatchingTheDomainTheRequestArrivedOn() throws Exception {
        String onClovlov = authorizationRedirectUriFor("clovlov.xyz");
        assertTrue(onClovlov.contains("redirect_uri=https://clovlov.xyz/login/oauth2/code/kakao"));

        String onClovlabcalss = authorizationRedirectUriFor("clovlabcalss.store");
        assertTrue(onClovlabcalss.contains("redirect_uri=https://clovlabcalss.store/login/oauth2/code/kakao"));
    }

    private String authorizationRedirectUriFor(String domain) throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/kakao")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", domain))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return result.getResponse().getHeader("Location");
    }
}
