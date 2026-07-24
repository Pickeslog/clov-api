package com.korit.clovapi.domain.notification;

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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FRIEND 알림 팬아웃 통합 테스트(계약 §13).
 *
 * <p>추억 작성·약속 등록/완료는 actor 본인을 제외한 ACTIVE 멤버에게, 레벨업은 방 전체(actor=null)에게.
 */
class NotificationFanOutIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenA;   // 방 생성자(활동 주체)
    private long userA;
    private String tokenB;   // 같은 방의 다른 멤버(수신자)
    private long userB;
    private long roomId;
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        AuthUser a = signUp("액터");
        tokenA = a.accessToken();
        userA = a.userId();
        roomId = createRoom("알림 팬아웃 방");

        AuthUser b = signUp("수신자");
        tokenB = b.accessToken();
        userB = b.userId();
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id, status) VALUES (?, ?, 'ACTIVE')", roomId, userB);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notifications WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM friendship_exp_logs WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM memories WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM plans WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", roomId);
        for (Long id : userIds) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", id);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    @Test
    void memoryWriteNotifiesOtherMembersButNotTheAuthor() throws Exception {
        createFreeMemory("짧은 추억");

        // 수신자 B: FRIEND 탭에 MEMORY_WRITE 1건, actor = 작성자 A
        mockMvc.perform(get("/api/v1/rooms/{roomId}/notifications?type=FRIEND", roomId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("FRIEND"))
                .andExpect(jsonPath("$.data.items[0].subType").value("MEMORY_WRITE"))
                .andExpect(jsonPath("$.data.items[0].actor.id").value(String.valueOf(userA)))
                .andExpect(jsonPath("$.data.items[0].payload").doesNotExist());

        // 작성자 A 본인은 받지 않는다.
        mockMvc.perform(get("/api/v1/rooms/{roomId}/notifications?type=FRIEND", roomId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void planCreateAndCompleteNotifyOtherMembers() throws Exception {
        long planId = createPlan();
        mockMvc.perform(post("/api/v1/plans/{planId}/complete", planId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // B: PLAN_CREATE + PLAN_COMPLETE 두 건(최신순)
        mockMvc.perform(get("/api/v1/rooms/{roomId}/notifications?type=FRIEND", roomId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].subType").value("PLAN_COMPLETE"))
                .andExpect(jsonPath("$.data.items[1].subType").value("PLAN_CREATE"))
                .andExpect(jsonPath("$.data.items[0].referenceId").value(String.valueOf(planId)));
    }

    @Test
    void levelUpNotifiesEveryoneWithNullActorAndLevelPayload() throws Exception {
        // 레벨 1 · 진행도 95에서 추억(+25)을 쓰면 100을 넘겨 레벨 2로 오른다.
        jdbcTemplate.update("UPDATE friendship_rooms SET exp_point = 95 WHERE id = ?", roomId);

        createFreeMemory("짧은 추억");

        // 레벨업은 방 전체 → 작성자 A도 받는다(추억은 본인 제외라 A에겐 LEVEL_UP만).
        mockMvc.perform(get("/api/v1/rooms/{roomId}/notifications?type=FRIEND", roomId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].subType").value("LEVEL_UP"))
                .andExpect(jsonPath("$.data.items[0].actor").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].payload.level").value(2))
                .andExpect(jsonPath("$.data.items[0].referenceId").value(String.valueOf(roomId)));

        // B는 MEMORY_WRITE + LEVEL_UP 둘 다.
        mockMvc.perform(get("/api/v1/rooms/{roomId}/notifications?type=FRIEND", roomId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    private long createFreeMemory(String content) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"추억\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private long createPlan() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms/{roomId}/plans", roomId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"바다 여행\",\"planDate\":\"2026-09-01\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private long createRoom(String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private AuthUser signUp(String nickname) throws Exception {
        String email = "noti-fan-" + UUID.randomUUID() + "@example.test";
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Abcd1234!\","
                                + "\"nickname\":\"" + nickname + "\","
                                + "\"agreements\":{\"service\":true,\"privacy\":true,\"marketing\":false}}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = JsonPath.read(signup.getResponse().getContentAsString(), "$.data.accessToken");
        long id = Long.parseLong(JsonPath.read(signup.getResponse().getContentAsString(), "$.data.user.id"));
        userIds.add(id);
        return new AuthUser(id, token);
    }

    private record AuthUser(long userId, String accessToken) {
    }
}
