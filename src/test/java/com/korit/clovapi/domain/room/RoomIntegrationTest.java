package com.korit.clovapi.domain.room;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String accessToken;
    private long userId;
    private Long roomId;
    private final List<Long> roomIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        AuthUser user = signUp("Room Test");
        accessToken = user.accessToken();
        userId = user.userId();
    }

    @AfterEach
    void cleanUp() {
        for (Long createdRoomId : roomIds) {
            jdbcTemplate.update("DELETE FROM friendship_exp_logs WHERE room_id = ?", createdRoomId);
            jdbcTemplate.update("DELETE FROM notifications WHERE room_id = ?", createdRoomId);
            jdbcTemplate.update("DELETE FROM plans WHERE room_id = ?", createdRoomId);
            jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", createdRoomId);
            jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", createdRoomId);
        }
        for (Long createdUserId : userIds) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", createdUserId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", createdUserId);
        }
    }

    @Test
    void findMyRoomsReturnsOnlyActiveMembershipsInFavoriteOrder() throws Exception {
        long regularRoomId = createRoom(accessToken, "Regular Room");
        long favoriteRoomId = createRoom(accessToken, "Favorite Room");

        mockMvc.perform(patch("/api/v1/rooms/{roomId}/favorite", favoriteRoomId)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isFavorite\":true}"))
                .andExpect(status().isOk());

        AuthUser otherUser = signUp("Other Room Test");
        createRoom(otherUser.accessToken(), "Non Member Room");
        long leftMemberRoomId = createRoom(otherUser.accessToken(), "Left Member Room");
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id, status) VALUES (?, ?, 'LEFT')",
                leftMemberRoomId, userId);

        mockMvc.perform(get("/api/v1/rooms").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(favoriteRoomId)))
                .andExpect(jsonPath("$.data.items[0].name").value("Favorite Room"))
                .andExpect(jsonPath("$.data.items[0].themeColor").value("#7CC6A6"))
                .andExpect(jsonPath("$.data.items[0].coverPhotoUrl").value("https://example.test/cover.jpg"))
                .andExpect(jsonPath("$.data.items[0].friendshipLevel").value(1))
                .andExpect(jsonPath("$.data.items[0].memberCount").value(1))
                .andExpect(jsonPath("$.data.items[0].isFavorite").value(true))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].createdAt").exists())
                .andExpect(jsonPath("$.data.items[1].id").value(String.valueOf(regularRoomId)))
                .andExpect(jsonPath("$.data.items[1].isFavorite").value(false));
    }

    @Test
    void findMyRoomsIncludesNextScheduledPlan() throws Exception {
        long tripRoom = createRoom(accessToken, "Trip Room");
        createRoom(accessToken, "Empty Room");
        jdbcTemplate.update(
                "INSERT INTO plans (room_id, writer_id, title, plan_date, status, memory_status) "
                        + "VALUES (?, ?, '제주 여행', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'SCHEDULED', 'NONE')",
                tripRoom, userId);

        mockMvc.perform(get("/api/v1/rooms").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.name=='Trip Room')].nextPlan.title",
                        org.hamcrest.Matchers.hasItem("제주 여행")))
                .andExpect(jsonPath("$.data.items[?(@.name=='Trip Room')].nextPlan.planDate").exists())
                // 약속 없는 방은 nextPlan = null → 필터 결과에 title 없음.
                .andExpect(jsonPath("$.data.items[?(@.name=='Empty Room')].nextPlan.title").isEmpty());
    }

    @Test
    void findMyRoomsReturnsAnEmptyItemsArrayWhenTheUserHasNoActiveRooms() throws Exception {
        mockMvc.perform(get("/api/v1/rooms").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    private AuthUser signUp(String nickname) throws Exception {
        String email = "room-it-" + UUID.randomUUID() + "@example.test";
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

    private long createRoom(String token, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"description\":\"Prepare together\","
                                + "\"themeColor\":\"#7CC6A6\",\"transportType\":\"airplane\","
                                + "\"coverPhotoUrl\":\"https://example.test/cover.jpg\",\"coverTitle\":\"Room\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long createdRoomId = Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
        roomIds.add(createdRoomId);
        return createdRoomId;
    }

    @Test
    void roomLifecycleAndMascotEndpointsFollowTheContract() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jeju Trip\",\"description\":\"Prepare together\","
                                + "\"themeColor\":\"#7CC6A6\",\"transportType\":\"airplane\","
                                + "\"coverPhotoUrl\":\"https://example.test/cover.jpg\",\"coverTitle\":\"Jeju\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.memberCount").value(1))
                .andReturn();
        roomId = Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
        roomIds.add(roomId);

        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Jeju Trip"));

        mockMvc.perform(patch("/api/v1/rooms/{roomId}", roomId)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Updated\",\"coverPhotoUrl\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Updated"))
                .andExpect(jsonPath("$.data.coverPhotoUrl").doesNotExist());
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE room_id = ?", Integer.class, roomId));

        mockMvc.perform(get("/api/v1/rooms/{roomId}/members", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].membershipId").isString())
                .andExpect(jsonPath("$.data.items[0].userId").isString())
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"));

        mockMvc.perform(patch("/api/v1/rooms/{roomId}/members/me/status-message", roomId)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusMessage\":\"Packing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusMessage").value("Packing"));

        mockMvc.perform(patch("/api/v1/rooms/{roomId}/favorite", roomId)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isFavorite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFavorite").value(true));

        for (int index = 0; index < 3; index++) {
            mockMvc.perform(post("/api/v1/rooms/{roomId}/mascot/interact", roomId)
                            .header("Authorization", bearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.expDelta").value(2))
                    .andExpect(jsonPath("$.data.remainingToday").value(2 - index));
        }
        mockMvc.perform(post("/api/v1/rooms/{roomId}/mascot/interact", roomId)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("MASCOT_INTERACTION_LIMIT_REACHED"));

        mockMvc.perform(get("/api/v1/rooms/{roomId}/exp-logs", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].id").isString());

        mockMvc.perform(get("/api/v1/rooms/{roomId}/level", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expPoint").value(6))
                .andExpect(jsonPath("$.data.remainingToNextLevel").value(494));

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/members/me", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/revive", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.scheduledDeleteAt").doesNotExist());
    }

    private String bearerToken() {
        return "Bearer " + accessToken;
    }

    private record AuthUser(long userId, String accessToken) {
    }
}
