package com.korit.clovapi.domain.user;

import com.jayway.jsonpath.JsonPath;
import com.korit.clovapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String email;
    private String accessToken;
    private long userId;

    private static final String PASSWORD = "Abcd1234!";

    @BeforeEach
    void setUp() throws Exception {
        email = "user-it-" + UUID.randomUUID() + "@example.test";
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\","
                                + "\"nickname\":\"클로버\","
                                + "\"agreements\":{\"service\":true,\"privacy\":true,\"marketing\":false}}"))
                .andExpect(status().isCreated())
                .andReturn();
        accessToken = JsonPath.read(signup.getResponse().getContentAsString(), "$.data.accessToken");
        userId = Long.parseLong(JsonPath.read(signup.getResponse().getContentAsString(), "$.data.user.id"));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_preferences WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void profileIsReadableAndPartiallyUpdatable() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(String.valueOf(userId)))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.nickname").value("클로버"))
                .andExpect(jsonPath("$.data.personalInviteCode").exists())
                .andExpect(jsonPath("$.data.isSocial").value(false));

        mockMvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새클로버\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새클로버"))
                .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    void passwordChangeVerifiesCurrentAndRevokesRefreshTokens() throws Exception {
        // 로그인으로 refresh 토큰을 하나 만들어 revoke 대상 확보
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
        Long liveBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Long.class, userId);
        assert liveBefore != null && liveBefore >= 1;

        // 현재 비번 불일치 → 401
        mockMvc.perform(patch("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Wrong123!\",\"newPassword\":\"Xyz98765!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        // 정상 변경 → 200, 이후 refresh 전부 revoke
        mockMvc.perform(patch("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Xyz98765!\"}"))
                .andExpect(status().isOk());

        Long liveAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Long.class, userId);
        assert liveAfter != null && liveAfter == 0;
    }

    @Test
    void deleteAnonymizesInPlaceWithoutRemovingRow() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk());

        Boolean anonymized = jdbcTemplate.queryForObject(
                "SELECT is_anonymized FROM users WHERE id = ?", Boolean.class, userId);
        String nickname = jdbcTemplate.queryForObject(
                "SELECT nickname FROM users WHERE id = ?", String.class, userId);
        assert Boolean.TRUE.equals(anonymized);
        assert "언노운".equals(nickname);
    }

    @Test
    void preferencesReturnDefaultsThenApplyPartialUpdate() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/preferences").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.darkMode").value(false))
                .andExpect(jsonPath("$.data.letterTheme").value("postbox"))
                .andExpect(jsonPath("$.data.memoryCardTheme").value("clothesline"))
                .andExpect(jsonPath("$.data.mascotType").value("crobi"));

        mockMvc.perform(patch("/api/v1/users/me/preferences").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"darkMode\":true,\"customColor\":\"#7CC6A6\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.darkMode").value(true))
                .andExpect(jsonPath("$.data.customColor").value("#7CC6A6"))
                .andExpect(jsonPath("$.data.mascotType").value("crobi"));
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }
}
