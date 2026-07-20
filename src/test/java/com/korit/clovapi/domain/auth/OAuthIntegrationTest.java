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
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("OAUTH_CODE_INVALID"));
    }

    @Test
    void startsEachConfiguredOAuthProvider() throws Exception {
        for (String provider : new String[]{"kakao", "naver", "google"}) {
            mockMvc.perform(get("/oauth2/authorization/" + provider))
                    .andExpect(status().is3xxRedirection());
        }
    }
}
