package com.korit.clovapi.domain.memory;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String writerToken;
    private long writerId;
    private String otherToken;
    private long otherId;
    private Long roomId;
    private Long memoryId;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult writerSignup = signup("comment-writer-" + UUID.randomUUID() + "@example.test", "Comment Writer");
        writerToken = JsonPath.read(writerSignup.getResponse().getContentAsString(), "$.data.accessToken");
        writerId = Long.parseLong(JsonPath.read(writerSignup.getResponse().getContentAsString(), "$.data.user.id"));

        MvcResult otherSignup = signup("comment-other-" + UUID.randomUUID() + "@example.test", "Comment Other");
        otherToken = JsonPath.read(otherSignup.getResponse().getContentAsString(), "$.data.accessToken");
        otherId = Long.parseLong(JsonPath.read(otherSignup.getResponse().getContentAsString(), "$.data.user.id"));

        MvcResult room = mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Comment Room\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        roomId = Long.parseLong(JsonPath.read(room.getResponse().getContentAsString(), "$.data.id"));
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id) VALUES (?, ?)", roomId, otherId);

        MvcResult memory = mockMvc.perform(post("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Comment memory\",\"content\":\"Body\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        memoryId = Long.parseLong(JsonPath.read(memory.getResponse().getContentAsString(), "$.data.id"));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM memory_comments WHERE memory_id = ?", memoryId);
        jdbcTemplate.update("DELETE FROM memories WHERE id = ?", memoryId);
        jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", roomId);
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN (?, ?)", writerId, otherId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", writerId, otherId);
    }

    @Test
    void commentLifecycleFollowsTheContract() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/memories/{memoryId}/comments", memoryId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"너 진짜 웃겼어\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.writer.id").value(String.valueOf(otherId)))
                .andExpect(jsonPath("$.data.content").value("너 진짜 웃겼어"))
                .andReturn();
        long commentId = Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/memories/{memoryId}/comments", memoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(commentId)))
                .andExpect(jsonPath("$.data.items[0].writer.nickname").value("Comment Other"));

        mockMvc.perform(get("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentCount").value(1));

        mockMvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_WRITER"));

        mockMvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/memories/{memoryId}/comments", memoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void nonMemberCannotListComments() throws Exception {
        MvcResult outsiderSignup = signup("comment-outsider-" + UUID.randomUUID() + "@example.test", "Outsider");
        String outsiderToken = JsonPath.read(outsiderSignup.getResponse().getContentAsString(), "$.data.accessToken");
        long outsiderId = Long.parseLong(JsonPath.read(outsiderSignup.getResponse().getContentAsString(), "$.data.user.id"));

        try {
            mockMvc.perform(get("/api/v1/memories/{memoryId}/comments", memoryId)
                            .header("Authorization", "Bearer " + outsiderToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ROOM_MEMBER_NOT_FOUND"));
        } finally {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", outsiderId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", outsiderId);
        }
    }

    private MvcResult signup(String email, String nickname) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Abcd1234!\","
                                + "\"nickname\":\"" + nickname + "\","
                                + "\"agreements\":{\"service\":true,\"privacy\":true,\"marketing\":false}}"))
                .andExpect(status().isCreated())
                .andReturn();
    }
}
