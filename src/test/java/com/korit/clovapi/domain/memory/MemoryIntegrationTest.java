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
        // 약속 연결 추억 등록이 골드도 지급하면서(§15-4) writer가 user_wallets 행을 갖게 됐다 —
        // 지우지 않으면 users DELETE가 FK(fk_wallet_transactions_user/fk_user_wallets_user)에 걸린다.
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE user_id IN (?, ?)", writerId, otherId);
        jdbcTemplate.update("DELETE FROM user_wallets WHERE user_id IN (?, ?)", writerId, otherId);
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

    // 자유 추억 골드는 두 관문을 다 통과해야 지급된다(계약 §15-4) — 본문 3자 이상 + 하루 10회 미만.
    // 어느 쪽에 걸려도 글은 정상 저장되고 201이 나간다. earnedGold만 0이다.
    //
    // ★ 길이 조건은 "빈 글만 거르는" 장치라 경계가 낮다(3자). 채굴을 막는 것은 횟수 캡이다.
    @Test
    void freeMemoryGoldRequiresThreeCharsAndStopsAfterTenPerDay() throws Exception {
        // ① 본문 2자 — 한 글자 모자라서 지급되지 않는다. 글은 정상 생성된다.
        createFreeMemoryExpectingGold("AA", 0);

        // ② 본문 3자 — 경계값이 지급된다(>= 판정임을 못박는다).
        createFreeMemoryExpectingGold("BBB", 200);

        // ③ 짧은 글은 횟수를 소모하지 않는다 — 지급이 없으면 원장 행도 없고, 횟수는 원장을 센다.
        //    공백만 있는 본문도 trim 후 0자라 여기 걸린다.
        for (int i = 0; i < 5; i++) {
            createFreeMemoryExpectingGold("  ", 0);
        }

        // ④ 3자 이상으로 9건 더 채워 하루 10회를 소진한다(②의 1건 + 9건).
        for (int i = 0; i < 9; i++) {
            createFreeMemoryExpectingGold("D".repeat(25), 200);
        }

        // ⑤ 11번째는 429가 아니라 201 + earnedGold 0이다 — 글쓰기를 막는 건 골드 캡이 할 일이 아니다.
        createFreeMemoryExpectingGold("E".repeat(25), 0);

        // ⑥ 원장에는 지급된 10건만 남는다. 하루 최대 2,000(200 × 10)이다.
        Long total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM wallet_transactions WHERE user_id = ? AND reason = ?",
                Long.class, writerId, "EARN_MEMORY_FREE");
        org.junit.jupiter.api.Assertions.assertEquals(2000L, total);
    }

    // 조회 응답에는 earnedGold가 실리지 않는다(계약 §10) — 그때는 지급이 일어나지 않으므로,
    // 0을 보내면 "캡에 걸려 0원"과 구분되지 않는다.
    @Test
    void earnedGoldAppearsOnCreateOnlyNotOnRead() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Gold\",\"content\":\"" + "F".repeat(30) + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.earnedGold").value(200))
                .andReturn();
        long memoryId = Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.earnedGold").doesNotExist());

        mockMvc.perform(patch("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Gold (edited)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.earnedGold").doesNotExist());
    }

    // 하루 총 상한 합산은 EARN_ 접두사만 센다(계약 §15-4). ADMIN_GRANT(운영 지급)가 합산에
    // 잡히면 지급받은 날 정상 획득이 통째로 막힌다.
    //
    // ★ 이 테스트는 LIKE 이스케이프가 깨지는 것도 같이 잡는다. ESCAPE 절이 빠지면 '_'가
    // 임의의 한 글자로 매칭돼 다른 사유까지 합산에 걸리고, 이스케이프 문자로 백슬래시를
    // 쓰면 MySQL이 문자열 리터럴에서 먼저 해석해 구문 오류가 난다.
    @Test
    void adminGrantIsExcludedFromTheDailyEarnCap() throws Exception {
        // 상한(6,000)을 한참 넘는 운영 지급을 오늘 날짜로 원장에 직접 넣는다.
        jdbcTemplate.update("INSERT INTO user_wallets (user_id, balance) VALUES (?, ?)"
                + " ON DUPLICATE KEY UPDATE balance = VALUES(balance)", writerId, 100000);
        jdbcTemplate.update("INSERT INTO wallet_transactions (user_id, reason, amount, balance_after)"
                + " VALUES (?, 'ADMIN_GRANT', ?, ?)", writerId, 100000, 100000);

        // 합산이 EARN_ 접두사로만 되면 오늘 획득량은 아직 0이라 정상 지급된다.
        createFreeMemoryExpectingGold("G".repeat(25), 200);
    }

    private void createFreeMemoryExpectingGold(String content, int expectedGold) throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Free\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.earnedGold").value(expectedGold));
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
        // 8 = 계약 확정값(screen-spec-source/03-memory-feed-screen.md, 리더 결정 2026-07-30).
        // 프론트(clov-web Feed.jsx MEMORY_PHOTO_LIMIT)와 같은 값이어야 한다 — 여기가 낮으면
        // 화면에서 고를 수 있는 사진이 업로드에서 507로 튕긴다(프론트 15 vs 서버 10이었다).
        // 상수를 바꾸면 이 테스트가 먼저 깨지도록 경계(8 성공 / 9번째 507)를 그대로 확인한다.
        long memoryId = createFreeMemory();
        for (int i = 0; i < 8; i++) {
            commitImage(memoryId, "https://cdn.test/q" + i + ".jpg");
        }
        mockMvc.perform(get("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images.length()").value(8));

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

    // clov-api#98 — 삭제 시 plan.memory_status를 되돌리되 다른 멤버 기록이 남아 있으면 건드리지
    // 않는다. 되돌아온 뒤엔 재작성이 500이 아니라 정상 처리(같은 행을 되살림)되어야 한다.
    @Test
    void deletingPlanMemoryRevertsStatusUnlessOtherMemberStillHasOneAndRewriteRevivesInstead() throws Exception {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO plans (room_id, writer_id, title, status, memory_status) "
                            + "VALUES (?, ?, 'Camping', 'COMPLETED', 'CANDIDATE')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, roomId);
            ps.setLong(2, writerId);
            return ps;
        }, keyHolder);
        planId = keyHolder.getKey().longValue();
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id) VALUES (?, ?)", roomId, otherId);

        MvcResult writerCreated = mockMvc.perform(post("/api/v1/plans/{planId}/memories", planId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Camping v1\",\"content\":\"first\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long writerMemoryId = Long.parseLong(JsonPath.read(writerCreated.getResponse().getContentAsString(), "$.data.id"));
        commitImage(writerMemoryId, "https://cdn.test/before-delete.jpg");

        mockMvc.perform(post("/api/v1/plans/{planId}/memories", planId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Camping (other view)\",\"content\":\"also there\"}"))
                .andExpect(status().isCreated());

        // 작성자만 삭제 — 다른 멤버(other) 기록이 살아 있으므로 WRITTEN 유지.
        mockMvc.perform(delete("/api/v1/memories/{memoryId}", writerMemoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals("WRITTEN",
                jdbcTemplate.queryForObject("SELECT memory_status FROM plans WHERE id = ?", String.class, planId));

        // 재작성 시도 — 고치기 전이었으면 uk_memories_plan_writer 위반으로 500이 났다.
        MvcResult revived = mockMvc.perform(post("/api/v1/plans/{planId}/memories", planId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Camping v2\",\"content\":\"rewritten\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Camping v2"))
                .andReturn();
        long revivedMemoryId = Long.parseLong(JsonPath.read(revived.getResponse().getContentAsString(), "$.data.id"));
        // 같은 행이 되살아난 것이지 새 행이 아니다.
        org.junit.jupiter.api.Assertions.assertEquals(writerMemoryId, revivedMemoryId);

        // 삭제 전 사진은 새 기록에 이어받지 않는다(빈 상태로 되살아남).
        mockMvc.perform(get("/api/v1/memories/{memoryId}", revivedMemoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images.length()").value(0));

        // 재작성으로 다시 WRITTEN.
        org.junit.jupiter.api.Assertions.assertEquals("WRITTEN",
                jdbcTemplate.queryForObject("SELECT memory_status FROM plans WHERE id = ?", String.class, planId));

        // 이제 두 기록 다 지우면 — 아무도 안 남았으니 CANDIDATE로 되돌아간다.
        mockMvc.perform(delete("/api/v1/memories/{memoryId}", revivedMemoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals("WRITTEN",
                jdbcTemplate.queryForObject("SELECT memory_status FROM plans WHERE id = ?", String.class, planId));

        mockMvc.perform(get("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1)); // other의 기록만 남음
    }

    // clov-api#98 — PATCH planId로 연결/이동/해제. 완료 안 된 약속·중복·다른 방 약속은 거부되고,
    // 옛 약속은 마지막 기록이 떠나면 CANDIDATE로 되돌아간다.
    @Test
    void updatingMemoryPlanLinkConnectsMovesDetachesAndRejectsInvalidTargets() throws Exception {
        long memoryId = createFreeMemory();
        long planA = insertPlan(roomId, "CANDIDATE");
        long planB = insertPlan(roomId, "CANDIDATE");
        long planIncomplete = insertPlan(roomId, "NONE");

        try {
            // 연결.
            mockMvc.perform(patch("/api/v1/memories/{memoryId}", memoryId)
                            .header("Authorization", "Bearer " + writerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planId\":\"" + planA + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.planId").value(String.valueOf(planA)));
            org.junit.jupiter.api.Assertions.assertEquals("WRITTEN",
                    jdbcTemplate.queryForObject("SELECT memory_status FROM plans WHERE id = ?", String.class, planA));

            // 미완료 약속으로 연결 시도 → 409.
            long memory2 = createFreeMemory();
            mockMvc.perform(patch("/api/v1/memories/{memoryId}", memory2)
                            .header("Authorization", "Bearer " + writerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planId\":\"" + planIncomplete + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("PLAN_NOT_COMPLETED"));

            // 이미 내가 planA를 쓴 상태 → 다른 추억으로 또 연결 시도하면 409.
            mockMvc.perform(patch("/api/v1/memories/{memoryId}", memory2)
                            .header("Authorization", "Bearer " + writerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planId\":\"" + planA + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("MEMORY_ALREADY_WRITTEN"));

            // 이동 — planA에서 planB로. planA는 아무도 안 남았으니 CANDIDATE로 되돌아간다.
            mockMvc.perform(patch("/api/v1/memories/{memoryId}", memoryId)
                            .header("Authorization", "Bearer " + writerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planId\":\"" + planB + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.planId").value(String.valueOf(planB)));
            org.junit.jupiter.api.Assertions.assertEquals("CANDIDATE",
                    jdbcTemplate.queryForObject("SELECT memory_status FROM plans WHERE id = ?", String.class, planA));
            org.junit.jupiter.api.Assertions.assertEquals("WRITTEN",
                    jdbcTemplate.queryForObject("SELECT memory_status FROM plans WHERE id = ?", String.class, planB));

            // 해제 — planB도 CANDIDATE로.
            mockMvc.perform(patch("/api/v1/memories/{memoryId}", memoryId)
                            .header("Authorization", "Bearer " + writerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planId\":null}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.planId").doesNotExist());
            org.junit.jupiter.api.Assertions.assertEquals("CANDIDATE",
                    jdbcTemplate.queryForObject("SELECT memory_status FROM plans WHERE id = ?", String.class, planB));

            // 다른 방 약속으로 연결 시도 → 존재하지 않는 것처럼 404.
            long otherRoomId = createRoom(writerToken, "Other Room");
            long otherRoomPlan = insertPlan(otherRoomId, "CANDIDATE");
            try {
                mockMvc.perform(patch("/api/v1/memories/{memoryId}", memoryId)
                                .header("Authorization", "Bearer " + writerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"planId\":\"" + otherRoomPlan + "\"}"))
                        .andExpect(status().isNotFound());
            } finally {
                jdbcTemplate.update("DELETE FROM plans WHERE id = ?", otherRoomPlan);
                jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", otherRoomId);
                jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", otherRoomId);
            }
        } finally {
            jdbcTemplate.update("DELETE FROM plans WHERE id IN (?, ?, ?)", planA, planB, planIncomplete);
        }
    }

    // 요청 본문의 문자열 ID가 숫자가 아니면 400이어야 한다. Long.parseLong을 그대로 태우면
    // NumberFormatException이 @ExceptionHandler(Exception.class)까지 올라가 500이 난다.
    @Test
    void nonNumericIdsInRequestBodyAreRejectedWith400() throws Exception {
        long memoryId = createFreeMemory();

        mockMvc.perform(patch("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        // long 범위를 넘는 숫자도 같은 경로다(자릿수만 맞으면 통과하는 정규식으로는 못 막는다).
        mockMvc.perform(patch("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"99999999999999999999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(patch("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantUserIds\":[\"abc\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/memories", roomId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"content\":\"c\",\"participantUserIds\":[\"abc\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // 'SKIPPED'는 "이 약속은 추억을 안 남긴다"는 사용자 결정이라 추억을 지워도 되돌리면 안 된다.
    // PlanService.skip()에 상태 가드가 없어서 WRITTEN인 약속도 SKIPPED가 될 수 있다.
    @Test
    void deletingMemoryDoesNotUndoSkippedPlanStatus() throws Exception {
        // 클래스 필드에 담아 @AfterEach가 정리하게 한다 — 추억은 소프트 삭제라 행이 남아 있어서,
        // memories보다 plans를 먼저 지우면 FK 제약에 걸린다(cleanUp이 그 순서를 지킨다).
        planId = insertPlan(roomId, "CANDIDATE");

        MvcResult created = mockMvc.perform(post("/api/v1/plans/{planId}/memories", planId)
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Trip\",\"content\":\"went\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long memoryId = Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        // 다른 멤버가 (stale UI로) 스킵을 누른 상황 — skip()은 WRITTEN이어도 막지 않는다.
        mockMvc.perform(post("/api/v1/plans/{planId}/skip-memory", planId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals("SKIPPED",
                jdbcTemplate.queryForObject("SELECT memory_status FROM plans WHERE id = ?", String.class, planId));

        mockMvc.perform(delete("/api/v1/memories/{memoryId}", memoryId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk());

        // 고치기 전이었으면 여기서 CANDIDATE가 되어 스킵이 조용히 풀렸다.
        org.junit.jupiter.api.Assertions.assertEquals("SKIPPED",
                jdbcTemplate.queryForObject("SELECT memory_status FROM plans WHERE id = ?", String.class, planId));
    }

    private long insertPlan(long forRoomId, String memoryStatus) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO plans (room_id, writer_id, title, status, memory_status) "
                            + "VALUES (?, ?, 'Plan', 'COMPLETED', ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, forRoomId);
            ps.setLong(2, writerId);
            ps.setString(3, memoryStatus);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private long createRoom(String token, String name) throws Exception {
        MvcResult room = mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(JsonPath.read(room.getResponse().getContentAsString(), "$.data.id"));
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
