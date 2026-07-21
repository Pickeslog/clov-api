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

    private String email;
    private String accessToken;
    private long userId;
    private Long roomId;

    @BeforeEach
    void setUp() throws Exception {
        email = "room-it-" + UUID.randomUUID() + "@example.test";
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Abcd1234!\","
                                + "\"nickname\":\"Room Test\","
                                + "\"agreements\":{\"service\":true,\"privacy\":true,\"marketing\":false}}"))
                .andExpect(status().isCreated())
                .andReturn();
        accessToken = JsonPath.read(signup.getResponse().getContentAsString(), "$.data.accessToken");
        userId = Long.parseLong(JsonPath.read(signup.getResponse().getContentAsString(), "$.data.user.id"));
    }

    @AfterEach
    void cleanUp() {
        if (roomId != null) {
            jdbcTemplate.update("DELETE FROM friendship_exp_logs WHERE room_id = ?", roomId);
            jdbcTemplate.update("DELETE FROM notifications WHERE room_id = ?", roomId);
            jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", roomId);
            jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", roomId);
        }
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
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
}
