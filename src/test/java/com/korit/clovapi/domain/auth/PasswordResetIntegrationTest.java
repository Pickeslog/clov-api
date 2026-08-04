package com.korit.clovapi.domain.auth;

import com.jayway.jsonpath.JsonPath;
import com.korit.clovapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약 §4-4 비밀번호 재설정.
 *
 * <p>토큰 원문은 메일로만 나가고 API 응답에는 없다. 테스트는 <b>메일을 가로채는 대신</b>
 * DB의 해시와 대조하는 방식을 쓴다 — 서비스와 같은 SHA-256 hex로 후보 토큰을 해싱해
 * 행이 실제로 생겼는지, 사용/폐기 상태가 어떻게 바뀌는지 본다.
 */
class PasswordResetIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String email;
    private String socialEmail;

    @BeforeEach
    void setUp() {
        email = "pwreset-it-" + UUID.randomUUID() + "@example.test";
        socialEmail = "pwreset-social-" + UUID.randomUUID() + "@example.test";
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.query(
                "SELECT id FROM users WHERE email IN (?, ?)",
                (resultSet, rowNum) -> resultSet.getLong("id"),
                email, socialEmail
        ).forEach(userId -> {
            jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        });
    }

    @Test
    void forgotRespondsIdenticallyForUnknownSocialOnlyAndNormalAccounts() throws Exception {
        signup();
        createSocialOnlyUser();

        // 세 경로 모두 200 + data:null 이어야 한다. 하나라도 다르면 계정 열거가 된다.
        forgot(email).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        forgot(socialEmail).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        forgot("nobody-" + UUID.randomUUID() + "@example.test").andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 응답은 같아도 토큰 발급은 이메일 계정에만 일어난다.
        assertThat(tokenCount(email)).isEqualTo(1);
        assertThat(tokenCount(socialEmail)).isZero();
    }

    @Test
    void resetConsumesTokenRevokesSessionsAndAllowsLoginWithNewPassword() throws Exception {
        signup();
        String refreshToken = login("Abcd1234!");

        forgot(email).andExpect(status().isOk());
        String token = issuedTokenFor(email);

        mockMvc.perform(get("/api/v1/auth/password/reset").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(token, "Zxcv9876!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 1회용 — 같은 토큰 재사용은 거부된다.
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(token, "Qwer5678!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_RESET_TOKEN_INVALID"));

        // 검증 엔드포인트도 소모된 토큰을 거부한다.
        mockMvc.perform(get("/api/v1/auth/password/reset").param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_RESET_TOKEN_INVALID"));

        // 재설정 전에 발급된 refresh 세션은 전부 끊긴다.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("Abcd1234!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("Zxcv9876!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString());
    }

    @Test
    void reissuingRevokesThePreviousLinkSoOnlyOneStaysAlive() throws Exception {
        signup();

        forgot(email).andExpect(status().isOk());
        String first = issuedTokenFor(email);

        forgot(email).andExpect(status().isOk());
        String second = issuedTokenFor(email);
        assertThat(second).isNotEqualTo(first);

        mockMvc.perform(get("/api/v1/auth/password/reset").param("token", first))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_RESET_TOKEN_INVALID"));
        mockMvc.perform(get("/api/v1/auth/password/reset").param("token", second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    void rejectsForgedTokensAndWeakPasswordsAndLimitsRequestRate() throws Exception {
        signup();

        mockMvc.perform(get("/api/v1/auth/password/reset").param("token", "not-a-real-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_RESET_TOKEN_INVALID"));

        forgot(email).andExpect(status().isOk());
        String token = issuedTokenFor(email);

        // 비밀번호 정책 위반은 토큰을 소모하지 않는다(@Valid가 컨트롤러 진입 전에 막는다).
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(token, "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/auth/password/reset").param("token", token))
                .andExpect(status().isOk());

        // 창 안에서 4번째 요청은 거부된다(위에서 이미 1회 사용).
        forgot(email).andExpect(status().isOk());
        forgot(email).andExpect(status().isOk());
        forgot(email).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    private void signup() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Abcd1234!\","
                                + "\"nickname\":\"Reset Test\","
                                + "\"agreements\":{\"service\":true,\"privacy\":true,\"marketing\":false}}"))
                .andExpect(status().isCreated());
    }

    /** 소셜 전용 계정 = password NULL. 가입 API로는 만들 수 없어 직접 넣는다. */
    private void createSocialOnlyUser() {
        jdbcTemplate.update(
                "INSERT INTO users (email, password, nickname, oauth_provider, oauth_subject,"
                        + " terms_agreed_at, privacy_agreed_at)"
                        + " VALUES (?, NULL, ?, 'kakao', ?, NOW(), NOW())",
                socialEmail, "Social Test", UUID.randomUUID().toString()
        );
    }

    private String login(String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.refreshToken");
    }

    private ResultActions forgot(String targetEmail) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + targetEmail + "\"}"));
    }

    /**
     * 테스트가 쓸 토큰 원문을 확보한다.
     *
     * <p>DB에는 해시만 있고 원문은 메일로만 나가므로 되돌릴 수 없다. 그래서 <b>방금 발급된 행의
     * 해시를 테스트가 아는 값의 해시로 갈아끼운다.</b> 서비스 입장에서는 구분할 수 없는 정상 토큰이라
     * 만료·1회용·폐기 로직을 그대로 검증할 수 있다. (메일 발송기를 목으로 가로채는 방법도 있지만,
     * 그러면 저장·판정 경로가 아니라 발송 경로를 검증하게 된다.)
     */
    private String issuedTokenFor(String targetEmail) {
        String rawToken = "test-token-" + UUID.randomUUID();
        Long tokenId = jdbcTemplate.queryForObject(
                "SELECT t.id FROM password_reset_tokens t JOIN users u ON u.id = t.user_id"
                        + " WHERE u.email = ? AND t.used_at IS NULL AND t.revoked_at IS NULL"
                        + " ORDER BY t.id DESC LIMIT 1",
                Long.class, targetEmail
        );
        jdbcTemplate.update("UPDATE password_reset_tokens SET token_hash = ? WHERE id = ?", sha256Hex(rawToken), tokenId);
        return rawToken;
    }

    private int tokenCount(String targetEmail) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens t JOIN users u ON u.id = t.user_id WHERE u.email = ?",
                Integer.class, targetEmail
        );
        return count == null ? 0 : count;
    }

    private String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String loginBody(String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String resetBody(String token, String newPassword) {
        return "{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}";
    }
}
