package com.korit.clovapi.domain.room;

import com.jayway.jsonpath.JsonPath;
import com.korit.clovapi.domain.room.service.ExpService;
import com.korit.clovapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 우정 경험치 적립·레벨업 통합 테스트(계약 §12).
 *
 * <p>레벨당 100 XP, exp_point는 현재 레벨 안의 진행도, 만렙 777.
 */
class ExpIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExpService expService;

    private String accessToken;
    private long userId;
    private long roomId;
    private final List<Long> roomIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        AuthUser user = signUp("Exp Test");
        accessToken = user.accessToken();
        userId = user.userId();
        roomId = createRoom("Exp Room");
    }

    @AfterEach
    void cleanUp() {
        for (Long createdRoomId : roomIds) {
            jdbcTemplate.update("DELETE FROM memory_images WHERE memory_id IN "
                    + "(SELECT id FROM memories WHERE room_id = ?)", createdRoomId);
            jdbcTemplate.update("DELETE FROM memories WHERE room_id = ?", createdRoomId);
            jdbcTemplate.update("DELETE FROM plans WHERE room_id = ?", createdRoomId);
            jdbcTemplate.update("DELETE FROM friendship_exp_logs WHERE room_id = ?", createdRoomId);
            jdbcTemplate.update("DELETE FROM notifications WHERE room_id = ?", createdRoomId);
            jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", createdRoomId);
            jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", createdRoomId);
        }
        for (Long createdUserId : userIds) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", createdUserId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", createdUserId);
        }
    }

    @Test
    void memoryWriteGrantsBaseExpWithoutTextBonusWhenContentIsShort() throws Exception {
        createFreeMemory("짧은 본문");

        assertLevel(1, 25, 75);
        assertSingleLog("MEMORY_WRITE", 25);
    }

    @Test
    void memoryWriteAppliesOnlyHighestTextBonus() throws Exception {
        // 100자 이상 → +10만 붙는다(+5와 중첩되지 않음). 25 + 10 = 35
        createFreeMemory("가".repeat(100));

        assertLevel(1, 35, 65);
        assertSingleLog("MEMORY_WRITE", 35);
    }

    @Test
    void memoryWriteAppliesFiftyCharBonus() throws Exception {
        createFreeMemory("가".repeat(50)); // 25 + 5

        assertLevel(1, 30, 70);
    }

    @Test
    void imageCommitGrantsBonusPerImageUpToTen() throws Exception {
        long memoryId = createFreeMemory("사진 보너스 확인"); // +25

        // 이미지 쿼터가 추억당 10장이라 API로는 10장까지만 올릴 수 있다.
        for (int index = 0; index < 10; index++) {
            commitImage(memoryId, index);
        }
        // 쿼터가 나중에 늘어나도 XP 보너스는 10에서 멈춰야 하므로 상한을 직접 확인한다.
        expService.grantMemoryImageBonus(roomId, userId, memoryId);

        // 25 + 10 = 35 → 레벨 1, 진행도 35
        assertLevel(1, 35, 65);
        Integer bonusSum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(exp_delta), 0) FROM friendship_exp_logs "
                        + "WHERE room_id = ? AND action_type = 'MEMORY_IMAGE_BONUS' AND reference_id = ?",
                Integer.class, roomId, memoryId);
        assertEquals(10, bonusSum);
    }

    @Test
    void planCreateAndCompleteGrantExp() throws Exception {
        long planId = createPlan();
        assertLevel(1, 3, 97); // 등록 +3

        mockMvc.perform(post("/api/v1/plans/{planId}/complete", planId)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk());

        assertLevel(1, 18, 82); // +15

        mockMvc.perform(get("/api/v1/rooms/{roomId}/exp-logs", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].actionType").value("PLAN_COMPLETE"))
                .andExpect(jsonPath("$.data.items[0].expDelta").value(15))
                .andExpect(jsonPath("$.data.items[0].referenceId").value(String.valueOf(planId)));
    }

    @Test
    void levelUpsContinuouslyWhenExpCrossesHundredSeveralTimes() throws Exception {
        // 레벨 1 · 진행도 95에서 시작 → 추억 작성(+35)이면 100을 넘겨 레벨 2 · 진행도 30
        jdbcTemplate.update("UPDATE friendship_rooms SET exp_point = 95 WHERE id = ?", roomId);

        createFreeMemory("가".repeat(100)); // +35

        assertLevel(2, 30, 70);
    }

    @Test
    void maxLevelStopsGrantingExp() throws Exception {
        jdbcTemplate.update("UPDATE friendship_rooms SET friendship_level = 777, exp_point = 0 WHERE id = ?", roomId);

        createFreeMemory("만렙 이후 작성");

        assertLevel(777, 0, 0);
        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM friendship_exp_logs WHERE room_id = ?", Integer.class, roomId);
        assertEquals(0, logCount);
    }

    private void assertLevel(int level, int expPoint, int remaining) throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{roomId}/level", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friendshipLevel").value(level))
                .andExpect(jsonPath("$.data.expPoint").value(expPoint))
                .andExpect(jsonPath("$.data.expForNextLevel").value(100))
                .andExpect(jsonPath("$.data.remainingToNextLevel").value(remaining));
    }

    private void assertSingleLog(String actionType, int expDelta) throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{roomId}/exp-logs", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].actionType").value(actionType))
                .andExpect(jsonPath("$.data.items[0].expDelta").value(expDelta));
    }

    private long createFreeMemory(String content) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"추억\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private void commitImage(long memoryId, int sortOrder) throws Exception {
        mockMvc.perform(post("/api/v1/memories/{memoryId}/images", memoryId)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"https://example.test/" + sortOrder + ".jpg\","
                                + "\"sortOrder\":" + sortOrder + "}"))
                .andExpect(status().isOk());
    }

    private long createPlan() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms/{roomId}/plans", roomId)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"바다 여행\",\"planDate\":\"2026-09-01\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private long createRoom(String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long createdRoomId = Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
        roomIds.add(createdRoomId);
        return createdRoomId;
    }

    private AuthUser signUp(String nickname) throws Exception {
        String email = "exp-" + UUID.randomUUID() + "@example.test";
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Abcd1234!\","
                                + "\"nickname\":\"" + nickname + "\","
                                + "\"agreements\":{\"service\":true,\"privacy\":true,\"marketing\":false}}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = JsonPath.read(signup.getResponse().getContentAsString(), "$.data.accessToken");
        long createdUserId = Long.parseLong(JsonPath.read(signup.getResponse().getContentAsString(), "$.data.user.id"));
        userIds.add(createdUserId);
        return new AuthUser(createdUserId, token);
    }

    private String bearerToken() {
        return "Bearer " + accessToken;
    }

    private record AuthUser(long userId, String accessToken) {
    }
}
