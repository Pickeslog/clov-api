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
    void profileImagePresignReturnsSignedPutUrl() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/profile-image/presign").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"fileSize\":102400}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").value(org.hamcrest.Matchers.startsWith("https://")))
                .andExpect(jsonPath("$.data.uploadUrl").value(org.hamcrest.Matchers.containsString("X-Amz-Signature")))
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.containsString("users/" + userId + "/profile-")))
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.endsWith(".png")))
                .andExpect(jsonPath("$.data.expiresIn").value(300));
    }

    @Test
    void profileImagePresignRejectsMissingContentType() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/profile-image/presign").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileSize\":102400}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preferencesReturnDefaultsThenApplyPartialUpdate() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/preferences").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.darkMode").value(false))
                .andExpect(jsonPath("$.data.letterTheme").value("postbox"))
                // 겹침 카드가 명세 기본값(#70) — 'clothesline'로 되돌아가면 신규 가입자가 전원 빨랫줄이 된다
                .andExpect(jsonPath("$.data.memoryCardTheme").value("stack"))
                .andExpect(jsonPath("$.data.mascotType").value("crobi"));

        mockMvc.perform(patch("/api/v1/users/me/preferences").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"darkMode\":true,\"customColor\":\"#7CC6A6\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.darkMode").value(true))
                .andExpect(jsonPath("$.data.customColor").value("#7CC6A6"))
                // 안 보낸 필드는 그대로 남는다 — 이게 부분 수정의 핵심이라 지우지 않는다
                .andExpect(jsonPath("$.data.mascotType").value("crobi"));

        // 세 번째 캐릭터(버거노인)까지 왕복하는지는 따로 본다. mascotType만 보내고
        // 앞서 넣은 darkMode가 살아 있는지도 같이 확인해 반대 방향 부분 수정을 덮는다.
        mockMvc.perform(patch("/api/v1/users/me/preferences").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mascotType\":\"burgerOldman\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mascotType").value("burgerOldman"))
                .andExpect(jsonPath("$.data.darkMode").value(true));
    }

    // 계약 §5의 허용값 표를 서버가 실제로 막는지 본다. 검증이 없으면 표에 없는 값이 그대로
    // 저장되는데 에러가 안 나고 화면에서만 조용히 깨진다 — 프론트가 아는 값이 아니면
    // 기본값으로 떨어뜨려서, 사용자에게는 설정이 이유 없이 되돌아간 것으로 보인다.
    @Test
    void preferencesRejectValuesOutsideTheContractAllowList() throws Exception {
        String[] rejected = {
                "{\"mascotType\":\"aaaa\"}",
                "{\"mascotType\":\"robot\"}",      // 계약에 잘못 적혀 있던 옛 값 — 저장값은 rob이다
                "{\"memoryCardTheme\":\"coverflow\"}", // 프로토타입 내부 이름 — 프로덕션은 stack
                "{\"letterTheme\":\"envelope\"}",
        };
        for (String body : rejected) {
            mockMvc.perform(patch("/api/v1/users/me/preferences").header(HttpHeaders.AUTHORIZATION, bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }

        // 거부된 요청이 하나도 반영되지 않았는지 — 부분 수정이라 다른 필드까지 오염되면 안 된다
        mockMvc.perform(get("/api/v1/users/me/preferences").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mascotType").value("crobi"))
                .andExpect(jsonPath("$.data.memoryCardTheme").value("stack"))
                .andExpect(jsonPath("$.data.letterTheme").value("postbox"));

        // 허용값 전체가 통과한다 — 계약 §5 표에 값을 더할 때 여기와 @Pattern을 함께 고친다.
        // 셋 중 하나라도 빠지면 프론트에서 고를 수 있는 마스코트가 저장에서 400으로 튕긴다.
        for (String mascot : new String[]{"crobi", "rob", "burgerOldman", "takoGun", "kimCheolsu", "onyx"}) {
            mockMvc.perform(patch("/api/v1/users/me/preferences").header(HttpHeaders.AUTHORIZATION, bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mascotType\":\"" + mascot + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.mascotType").value(mascot));
        }

        // letterTheme 허용값 전체(postbox·giftbox)가 통과한다 — #109, 계약 §5에 giftbox 추가.
        for (String theme : new String[]{"postbox", "giftbox"}) {
            mockMvc.perform(patch("/api/v1/users/me/preferences").header(HttpHeaders.AUTHORIZATION, bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"letterTheme\":\"" + theme + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.letterTheme").value(theme));
        }
    }

    // #145 — 가입엔 @Past가 있는데 수정엔 없어서 미래 생년월일이 저장되던 버그.
    @Test
    void updateProfileRejectsFutureBirthdate() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthdate\":\"2999-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateProfileAcceptsPastBirthdate() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthdate\":\"1995-05-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthdate").value("1995-05-20"));
    }

    // @Past는 null을 통과시킨다 — birthdate를 안 보내는 부분 수정(닉네임만)이 여전히 되는지 확인.
    @Test
    void updateProfileWithoutBirthdateStillSucceeds() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"닉네임만\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("닉네임만"));
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }
}
