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
        // 댓글도 memories를 FK로 잡고 있다 — 안 지우면 아래 memories DELETE가 제약에 걸린다.
        jdbcTemplate.update("DELETE FROM memory_comments WHERE memory_id IN "
                + "(SELECT id FROM memories WHERE room_id = ?)", roomId);
        jdbcTemplate.update("DELETE FROM memories WHERE room_id = ?", roomId);
        if (planId != null) {
            jdbcTemplate.update("DELETE FROM plans WHERE id = ?", planId);
        }
        jdbcTemplate.update("DELETE FROM friendship_exp_logs WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM notifications WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", roomId);
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN (?, ?)", writerId, otherId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", writerId, otherId);
    }

    // 계약 §10 `title` ≤ 40. 목업(space.js:169)과 프론트 maxLength가 40이라 여기가 낮으면
    // 사용자가 화면에서 입력할 수 있는 제목이 저장 시 400으로 튕긴다(clov-web #148/#185).
    // 작성·수정 양쪽을 함께 본다 — 한쪽만 낮으면 수정 모드에서만 터져서 원인이 안 보인다.
    @Test
    void memoryTitleAcceptsFortyCharactersAndRejectsMore() throws Exception {
        String forty = "가".repeat(40);
        String fortyOne = "가".repeat(41);

        MvcResult created = mockMvc.perform(post("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + forty + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value(forty))
                .andReturn();
        long memoryId = Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + fortyOne + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(patch("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + forty + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(forty));

        mockMvc.perform(patch("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + fortyOne + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
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
                .andExpect(jsonPath("$.data.items[0].tags[0]").value("hiking"))
                .andExpect(jsonPath("$.data.items[0].content").value("We got lost"))
                .andExpect(jsonPath("$.data.items[0].imageCount").value(0))
                .andExpect(jsonPath("$.data.items[0].participants[0].id").value(String.valueOf(writerId)));

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
    void feedThumbnailReflectsCoverImage() throws Exception {
        long memoryId = createFreeMemory();
        // 커밋 2장 → 대표(sort_order 0)가 썸네일이 된다.
        commitImage(memoryId, "https://cdn.test/cover.jpg");
        commitImage(memoryId, "https://cdn.test/second.jpg");
        mockMvc.perform(get("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(memoryId)))
                .andExpect(jsonPath("$.data.items[0].thumbnailUrl").value("https://cdn.test/cover.jpg"))
                .andExpect(jsonPath("$.data.items[0].imageCount").value(2));
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

    // 한 줄 메시지는 한 추억당 작성자 1인 1개(계약 §10) — 중복은 409, 고쳐 쓰려면 PATCH,
    // 지웠으면 다시 쓸 수 있다. 사람별로는 막히지 않는다.
    @Test
    void commentIsLimitedToOnePerWriterAndCanBeEdited() throws Exception {
        long memoryId = createFreeMemory();
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id) VALUES (?, ?)", roomId, otherId);

        MvcResult created = mockMvc.perform(post("/api/v1/memories/{memoryId}/comments", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"first message\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("first message"))
                .andReturn();
        long commentId = Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        // 같은 사람이 같은 추억에 또 쓰면 409.
        mockMvc.perform(post("/api/v1/memories/{memoryId}/comments", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"second message\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COMMENT_ALREADY_EXISTS"));

        // 다른 멤버는 정상 작성(제약은 사람별이 아니라 (추억, 작성자) 쌍이다).
        mockMvc.perform(post("/api/v1/memories/{memoryId}/comments", memoryId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"me too\"}"))
                .andExpect(status().isCreated());

        // 남의 메시지는 못 고친다.
        mockMvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hijack\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_WRITER"));

        mockMvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"edited message\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(String.valueOf(commentId)))
                .andExpect(jsonPath("$.data.content").value("edited message"));

        mockMvc.perform(patch("/api/v1/comments/{commentId}", commentId + 1_000_000)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"nowhere\"}"))
                .andExpect(status().isNotFound());

        // 지웠으면 다시 쓸 수 있어야 한다(제약이 "영구 1회"가 되면 안 된다).
        mockMvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/memories/{memoryId}/comments", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"rewritten\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/memories/{memoryId}/comments", memoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));
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
