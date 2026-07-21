package com.korit.clovapi.domain.memory;

import com.jayway.jsonpath.JsonPath;
import com.korit.clovapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemoryIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String writerEmail;
    private String writerToken;
    private long writerId;
    private String otherEmail;
    private String otherToken;
    private long otherId;
    private Long roomId;
    private Long planId;

    @BeforeEach
    void setUp() throws Exception {
        writerEmail = "memory-writer-" + UUID.randomUUID() + "@example.test";
        MvcResult writerSignup = signup(writerEmail, "Memory Writer");
        writerToken = JsonPath.read(writerSignup.getResponse().getContentAsString(), "$.data.accessToken");
        writerId = Long.parseLong(JsonPath.read(writerSignup.getResponse().getContentAsString(), "$.data.user.id"));

        otherEmail = "memory-other-" + UUID.randomUUID() + "@example.test";
        MvcResult otherSignup = signup(otherEmail, "Memory Other");
        otherToken = JsonPath.read(otherSignup.getResponse().getContentAsString(), "$.data.accessToken");
        otherId = Long.parseLong(JsonPath.read(otherSignup.getResponse().getContentAsString(), "$.data.user.id"));

        MvcResult room = mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jeju Trip\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        roomId = Long.parseLong(JsonPath.read(room.getResponse().getContentAsString(), "$.data.id"));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM memory_images WHERE memory_id IN "
                + "(SELECT id FROM memories WHERE room_id = ?)", roomId);
        jdbcTemplate.update("DELETE FROM memory_participants WHERE memory_id IN "
                + "(SELECT id FROM memories WHERE room_id = ?)", roomId);
        jdbcTemplate.update("DELETE FROM memory_tags WHERE memory_id IN "
                + "(SELECT id FROM memories WHERE room_id = ?)", roomId);
        jdbcTemplate.update("DELETE FROM memories WHERE room_id = ?", roomId);
        if (planId != null) {
            jdbcTemplate.update("DELETE FROM plans WHERE id = ?", planId);
        }
        jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", roomId);
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN (?, ?)", writerId, otherId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", writerId, otherId);
    }

    @Test
    void freeMemoryLifecycleFollowsTheContract() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"First hike\",\"content\":\"We got lost\","
                                + "\"tags\":[\"hiking\",\"jeju\"],\"participantUserIds\":[\"" + writerId + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.planId").doesNotExist())
                .andExpect(jsonPath("$.data.writer.id").value(String.valueOf(writerId)))
                .andExpect(jsonPath("$.data.tags[0]").value("hiking"))
                .andExpect(jsonPath("$.data.participants[0].id").value(String.valueOf(writerId)))
                .andReturn();
        long memoryId = Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", "Bearer " + writerToken)
                        .param("tag", "hiking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(memoryId)))
                .andExpect(jsonPath("$.data.items[0].tags[0]").value("hiking"));

        mockMvc.perform(get("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("We got lost"));

        mockMvc.perform(patch("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"First hike (edited)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("First hike (edited)"))
                .andExpect(jsonPath("$.data.content").value("We got lost"));

        mockMvc.perform(get("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROOM_MEMBER_NOT_FOUND"));

        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id) VALUES (?, ?)", roomId, otherId);
        mockMvc.perform(patch("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_WRITER"));

        mockMvc.perform(delete("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void planBasedMemoryEnforcesCompletionAndUniqueness() throws Exception {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO plans (room_id, writer_id, title, status, memory_status) "
                            + "VALUES (?, ?, 'Jeju Plan', 'COMPLETED', 'NONE')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, roomId);
            ps.setLong(2, writerId);
            return ps;
        }, keyHolder);
        planId = keyHolder.getKey().longValue();

        mockMvc.perform(post("/api/v1/plans/{planId}/memories", planId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Plan memory\",\"content\":\"Done\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PLAN_NOT_COMPLETED"));

        jdbcTemplate.update("UPDATE plans SET memory_status = 'CANDIDATE' WHERE id = ?", planId);

        MvcResult created = mockMvc.perform(post("/api/v1/plans/{planId}/memories", planId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Plan memory\",\"content\":\"Done\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.planId").value(String.valueOf(planId)))
                .andReturn();

        String memoryStatus = jdbcTemplate.queryForObject(
                "SELECT memory_status FROM plans WHERE id = ?", String.class, planId);
        org.junit.jupiter.api.Assertions.assertEquals("WRITTEN", memoryStatus);

        mockMvc.perform(post("/api/v1/plans/{planId}/memories", planId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Second attempt\",\"content\":\"Again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEMORY_ALREADY_WRITTEN"));
    }

    @Test
    void memoryImagePresignCommitReorderDeleteFollowContractAndAuthorization() throws Exception {
        long memoryId = createFreeMemory();

        // presign (작성자) → 서명 PUT URL
        mockMvc.perform(post("/api/v1/memories/{memoryId}/images/presign", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"fileSize\":204800}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").value(org.hamcrest.Matchers.startsWith("https://")))
                .andExpect(jsonPath("$.data.uploadUrl").value(org.hamcrest.Matchers.containsString("X-Amz-Signature")))
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.containsString("memories/" + memoryId + "/")))
                .andExpect(jsonPath("$.data.expiresIn").value(300));

        // 커밋 3장 → sort_order 0,1,2
        long img0 = commitImage(memoryId, "https://cdn.test/a.jpg");
        long img1 = commitImage(memoryId, "https://cdn.test/b.jpg");
        long img2 = commitImage(memoryId, "https://cdn.test/c.jpg");

        mockMvc.perform(get("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images.length()").value(3))
                .andExpect(jsonPath("$.data.images[0].id").value(String.valueOf(img0)))
                .andExpect(jsonPath("$.data.images[2].id").value(String.valueOf(img2)));

        // 순서 재정렬(역순) → sort_order 재부여
        mockMvc.perform(patch("/api/v1/memories/{memoryId}/images/order", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[\"" + img2 + "\",\"" + img1 + "\",\"" + img0 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images[0].id").value(String.valueOf(img2)))
                .andExpect(jsonPath("$.data.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.images[2].id").value(String.valueOf(img0)));

        // 비작성자(멤버) → NOT_WRITER
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id) VALUES (?, ?)", roomId, otherId);
        mockMvc.perform(post("/api/v1/memories/{memoryId}/images/presign", memoryId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_WRITER"));
        mockMvc.perform(delete("/api/v1/memory-images/{imageId}", img0)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_WRITER"));

        // 작성자 삭제 → 2장 남음
        mockMvc.perform(delete("/api/v1/memory-images/{imageId}", img1)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images.length()").value(2));
    }

    @Test
    void imageQuotaReturns507WhenExceeded() throws Exception {
        long memoryId = createFreeMemory();
        for (int i = 0; i < 10; i++) {
            commitImage(memoryId, "https://cdn.test/q" + i + ".jpg");
        }
        mockMvc.perform(post("/api/v1/memories/{memoryId}/images/presign", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().is(507))
                .andExpect(jsonPath("$.error.code").value("STORAGE_QUOTA_EXCEEDED"));
        mockMvc.perform(post("/api/v1/memories/{memoryId}/images", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"https://cdn.test/over.jpg\"}"))
                .andExpect(status().is(507))
                .andExpect(jsonPath("$.error.code").value("STORAGE_QUOTA_EXCEEDED"));
    }

    private long createFreeMemory() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Photos\",\"content\":\"trip\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private long commitImage(long memoryId, String imageUrl) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/memories/{memoryId}/images", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"" + imageUrl + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(JsonPath.read(res.getResponse().getContentAsString(), "$.data.id"));
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
