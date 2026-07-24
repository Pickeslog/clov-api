package com.korit.clovapi.domain.plan;

import com.jayway.jsonpath.JsonPath;
import com.korit.clovapi.global.security.jwt.JwtTokenProvider;
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

class PlanIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private long userId;
    private long roomId;
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        userId = insertUser("Plan Writer");
        roomId = insertRoom();
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id) VALUES (?, ?)", roomId, userId);
    }

    @AfterEach
    void cleanUp() {
        List<Long> planIds = jdbcTemplate.queryForList(
                "SELECT id FROM plans WHERE room_id = ?",
                Long.class,
                roomId
        );
        for (Long planId : planIds) {
            jdbcTemplate.update("DELETE FROM plan_stage_photos WHERE plan_id = ?", planId);
            jdbcTemplate.update("DELETE FROM plan_checklists WHERE plan_id = ?", planId);
            jdbcTemplate.update("DELETE FROM plans WHERE id = ?", planId);
        }
        jdbcTemplate.update("DELETE FROM friendship_exp_logs WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM notifications WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", roomId);
        for (Long createdUserId : userIds) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", createdUserId);
        }
    }

    @Test
    void checklistCreateUpdateAndDeleteFollowTheContract() throws Exception {
        long planId = createPlan(tokenFor(userId), "Checklist plan", "2026-07-21");

        MvcResult created = mockMvc.perform(post("/api/v1/plans/{planId}/checklists", planId)
                        .header("Authorization", tokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Book tickets\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.content").value("Book tickets"))
                .andExpect(jsonPath("$.data.checked").value(false))
                .andReturn();
        long checklistId = Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(patch("/api/v1/checklists/{checklistId}", checklistId)
                        .header("Authorization", tokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Book train tickets\",\"checked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Book train tickets"))
                .andExpect(jsonPath("$.data.checked").value(true));

        mockMvc.perform(delete("/api/v1/checklists/{checklistId}", checklistId)
                        .header("Authorization", tokenFor(userId)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/plans/{planId}", planId).header("Authorization", tokenFor(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checklists.length()").value(0));
    }

    @Test
    void deletePlanRemovesChildChecklistsAndStagePhotos() throws Exception {
        long planId = createPlan(tokenFor(userId), "Deletable plan", "2026-07-24");
        // 자식 행 생성: 체크리스트 + 단계사진(PROPOSAL) — 둘 다 plans FK(no cascade).
        mockMvc.perform(post("/api/v1/plans/{planId}/checklists", planId)
                        .header("Authorization", tokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"pack bags\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/plans/{planId}/stage-photos", planId)
                        .header("Authorization", tokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"PROPOSAL\",\"imageUrl\":\"https://test/p.jpg\"}"))
                .andExpect(status().isOk());

        // 자식이 있어도 삭제가 성공해야 한다(예전엔 FK 위반으로 500).
        mockMvc.perform(delete("/api/v1/plans/{planId}", planId).header("Authorization", tokenFor(userId)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/plans/{planId}", planId).header("Authorization", tokenFor(userId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void presignReturnsSignedPutUrlForAvailableStage() throws Exception {
        long planId = createPlan(tokenFor(userId), "Presign plan", "2026-07-22");

        mockMvc.perform(post("/api/v1/plans/{planId}/stage-photos/presign", planId)
                        .header("Authorization", tokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"PROPOSAL\",\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").value(org.hamcrest.Matchers.startsWith("https://")))
                .andExpect(jsonPath("$.data.uploadUrl").value(org.hamcrest.Matchers.containsString("X-Amz-Signature")))
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.containsString("/plans/" + planId + "/proposal-")))
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.endsWith(".jpg")))
                .andExpect(jsonPath("$.data.expiresIn").value(300));
    }

    @Test
    void stagesAreSequentialImmutableAndCompleteCreatesMemoryCandidate() throws Exception {
        long planId = createPlan(tokenFor(userId), "Stage plan", "2026-07-22");

        mockMvc.perform(post("/api/v1/plans/{planId}/stage-photos/presign", planId)
                        .header("Authorization", tokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"SCHEDULING\",\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("STAGE_LOCKED"));

        for (String stage : List.of("PROPOSAL", "SCHEDULING", "CONFIRMED", "MEETING")) {
            mockMvc.perform(post("/api/v1/plans/{planId}/stage-photos", planId)
                            .header("Authorization", tokenFor(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"stage\":\"" + stage + "\",\"imageUrl\":\"https://test/" + stage + ".jpg\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.stage").value(stage))
                    .andExpect(jsonPath("$.data.state").value("DONE"));
        }

        mockMvc.perform(post("/api/v1/plans/{planId}/stage-photos", planId)
                        .header("Authorization", tokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"MEETING\",\"imageUrl\":\"https://test/again.jpg\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STAGE_ALREADY_UPLOADED"));
        mockMvc.perform(get("/api/v1/plans/{planId}/stage-photos", planId).header("Authorization", tokenFor(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[3].state").value("DONE"));

        mockMvc.perform(post("/api/v1/plans/{planId}/complete", planId).header("Authorization", tokenFor(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.memoryStatus").value("CANDIDATE"));
    }

    @Test
    void listFiltersAndWriterMembershipRulesAreEnforced() throws Exception {
        long scheduledPlanId = createPlan(tokenFor(userId), "Scheduled", "2026-07-20");
        long canceledPlanId = createPlan(tokenFor(userId), "Canceled", "2026-07-21");
        long skippedPlanId = createPlan(tokenFor(userId), "Skipped", "2026-07-22");
        mockMvc.perform(post("/api/v1/plans/{planId}/cancel", canceledPlanId).header("Authorization", tokenFor(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
        mockMvc.perform(post("/api/v1/plans/{planId}/skip-memory", skippedPlanId).header("Authorization", tokenFor(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memoryStatus").value("SKIPPED"));

        mockMvc.perform(get("/api/v1/rooms/{roomId}/plans", roomId)
                        .header("Authorization", tokenFor(userId))
                        .param("status", "CANCELED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(canceledPlanId)));
        mockMvc.perform(get("/api/v1/rooms/{roomId}/plans", roomId)
                        .header("Authorization", tokenFor(userId))
                        .param("from", "2026-07-21")
                        .param("to", "2026-07-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));

        long memberId = insertUser("Plan Member");
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id) VALUES (?, ?)", roomId, memberId);
        mockMvc.perform(patch("/api/v1/plans/{planId}", scheduledPlanId)
                        .header("Authorization", tokenFor(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Not allowed\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_WRITER"));
        mockMvc.perform(delete("/api/v1/plans/{planId}", scheduledPlanId).header("Authorization", tokenFor(memberId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_WRITER"));
        mockMvc.perform(post("/api/v1/plans/{planId}/cancel", scheduledPlanId).header("Authorization", tokenFor(memberId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_WRITER"));

        long nonMemberId = insertUser("Plan Non Member");
        mockMvc.perform(get("/api/v1/rooms/{roomId}/plans", roomId).header("Authorization", tokenFor(nonMemberId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROOM_MEMBER_NOT_FOUND"));
    }

    private long insertUser(String nickname) {
        String suffix = UUID.randomUUID().toString();
        String email = "plan-it-" + suffix + "@example.test";
        jdbcTemplate.update(
                "INSERT INTO users (email, nickname, personal_invite_code) VALUES (?, ?, ?)",
                email,
                nickname,
                "CLV-" + suffix.substring(0, 8)
        );
        long createdUserId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        userIds.add(createdUserId);
        return createdUserId;
    }

    private long insertRoom() {
        jdbcTemplate.update("INSERT INTO friendship_rooms (name) VALUES ('Plan integration room')");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM friendship_rooms ORDER BY id DESC LIMIT 1",
                Long.class
        );
    }

    private long createPlan(String token, String title, String planDate) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms/{roomId}/plans", roomId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"planDate\":\"" + planDate + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private String tokenFor(long targetUserId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(targetUserId);
    }
}
