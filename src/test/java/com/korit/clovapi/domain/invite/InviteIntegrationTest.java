package com.korit.clovapi.domain.invite;

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
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InviteIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> roomIds = new ArrayList<>();

    private long hostId;
    private long applicantId;
    private long roomId;

    @BeforeEach
    void setUp() {
        hostId = createUser("host");
        applicantId = createUser("applicant");
        roomId = createRoom();
        insertActiveMember(roomId, hostId);
    }

    @AfterEach
    void cleanUp() {
        for (long id : roomIds) {
            jdbcTemplate.update("DELETE FROM notifications WHERE room_id = ?", id);
            jdbcTemplate.update("DELETE FROM room_join_requests WHERE room_id = ?", id);
            jdbcTemplate.update("DELETE FROM room_invites WHERE room_id = ?", id);
            jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", id);
            jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", id);
        }
        for (long id : userIds) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", id);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    @Test
    void inviteAcceptanceCreatesPendingRequestThenAcceptAndUndoFollowTheContract() throws Exception {
        String inviteCode = createInvite();

        String joinRequestBody = mockMvc.perform(post("/api/v1/invites/accept")
                        .header("Authorization", bearer(applicantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.roomId").value(String.valueOf(roomId)))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long joinRequestId = Long.parseLong(JsonPath.read(joinRequestBody, "$.data.id"));

        mockMvc.perform(get("/api/v1/rooms/{roomId}/join-requests", roomId).header("Authorization", bearer(hostId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].applicant.id").value(String.valueOf(applicantId)));

        mockMvc.perform(post("/api/v1/join-requests/{id}/accept", joinRequestId).header("Authorization", bearer(hostId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipId").isString())
                .andExpect(jsonPath("$.data.userId").value(String.valueOf(applicantId)))
                .andExpect(jsonPath("$.data.undoDeadlineAt").exists());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_members WHERE room_id = ? AND user_id = ? AND status = 'ACTIVE'",
                Integer.class, roomId, applicantId));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE room_id = ?", Integer.class, roomId));

        mockMvc.perform(post("/api/v1/join-requests/{id}/undo", joinRequestId).header("Authorization", bearer(hostId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT status FROM room_join_requests WHERE id = ?", String.class, joinRequestId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_members WHERE room_id = ? AND user_id = ?", Integer.class, roomId, applicantId));

        mockMvc.perform(post("/api/v1/join-requests/{id}/reject", joinRequestId).header("Authorization", bearer(hostId)))
                .andExpect(status().isOk());
    }

    @Test
    void findMyJoinRequestsReturnsMyOutstandingRequests() throws Exception {
        long requestId = insertPendingJoinRequest(roomId, applicantId);

        mockMvc.perform(get("/api/v1/join-requests/mine").header("Authorization", bearer(applicantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(requestId)))
                .andExpect(jsonPath("$.data.items[0].roomId").value(String.valueOf(roomId)))
                .andExpect(jsonPath("$.data.items[0].roomName").exists())
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].roomStatus").value("ACTIVE"));

        // 신청하지 않은 사용자(host)는 빈 목록.
        mockMvc.perform(get("/api/v1/join-requests/mine").header("Authorization", bearer(hostId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void applicantCancelsOwnPendingRequest() throws Exception {
        long requestId = insertPendingJoinRequest(roomId, applicantId);

        // 남이 취소 시도 → NOT_FOUND(내 것 아님)
        mockMvc.perform(delete("/api/v1/join-requests/{id}", requestId).header("Authorization", bearer(hostId)))
                .andExpect(status().isNotFound());

        // 본인 취소 → 200, 행 삭제
        mockMvc.perform(delete("/api/v1/join-requests/{id}", requestId).header("Authorization", bearer(applicantId)))
                .andExpect(status().isOk());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_join_requests WHERE id = ?", Integer.class, requestId));
    }

    @Test
    void concurrentAcceptUsesOptimisticLock() throws Exception {
        long joinRequestId = insertPendingJoinRequest(roomId, applicantId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> accept = () -> mockMvc.perform(post("/api/v1/join-requests/{id}/accept", joinRequestId)
                            .header("Authorization", bearer(hostId)))
                    .andReturn().getResponse().getStatus();
            Future<Integer> first = executor.submit(accept);
            Future<Integer> second = executor.submit(accept);
            List<Integer> statuses = List.of(first.get(), second.get());
            assertTrue(statuses.contains(200));
            assertTrue(statuses.contains(409));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void capacityAndExpiredUndoKeepTheRequestState() throws Exception {
        for (int index = 0; index < 7; index++) {
            insertActiveMember(roomId, createUser("full-" + index));
        }
        long capacityRequestId = insertPendingJoinRequest(roomId, applicantId);
        mockMvc.perform(post("/api/v1/join-requests/{id}/accept", capacityRequestId).header("Authorization", bearer(hostId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ROOM_CAPACITY_EXCEEDED"));
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT status FROM room_join_requests WHERE id = ?", String.class, capacityRequestId));

        long otherRoomId = createRoom();
        insertActiveMember(otherRoomId, hostId);
        insertActiveMember(otherRoomId, applicantId);
        long expiredRequestId = insertAcceptedJoinRequest(otherRoomId, applicantId, hostId,
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        mockMvc.perform(post("/api/v1/join-requests/{id}/undo", expiredRequestId).header("Authorization", bearer(hostId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOIN_REQUEST_UNDO_EXPIRED"));
        assertEquals("ACCEPTED", jdbcTemplate.queryForObject(
                "SELECT status FROM room_join_requests WHERE id = ?", String.class, expiredRequestId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_members WHERE room_id = ? AND user_id = ?", Integer.class, otherRoomId, applicantId));
    }

    @Test
    void acceptRevivesPreviouslyLeftMemberOnRejoin() throws Exception {
        // 방을 나갔다(room_members에 LEFT 행 잔존) 코드로 재입장 신청 → 수락 시 bare insert가
        // UNIQUE(room_id,user_id) 위반으로 500나던 것을 revive(LEFT→ACTIVE)로 처리한다.
        insertLeftMember(roomId, applicantId);
        long joinRequestId = insertPendingJoinRequest(roomId, applicantId);

        mockMvc.perform(post("/api/v1/join-requests/{id}/accept", joinRequestId).header("Authorization", bearer(hostId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(String.valueOf(applicantId)));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_members WHERE room_id = ? AND user_id = ? AND status = 'ACTIVE'",
                Integer.class, roomId, applicantId));
        // 부활이지 새 insert가 아니므로 (room_id, user_id) 행은 여전히 하나뿐이어야 한다.
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_members WHERE room_id = ? AND user_id = ?",
                Integer.class, roomId, applicantId));
    }

    @Test
    void createRotatesCodeInPlaceKeepingOneRowPerRoom() throws Exception {
        String first = createInvite();
        String second = createInvite();   // 재발급

        // 방당 1행 — 재발급해도 새 행이 쌓이지 않는다(A안 핵심).
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_invites WHERE room_id = ?", Integer.class, roomId));
        // 코드는 제자리 회전(값이 바뀜), 상태는 ACTIVE 유지.
        assertNotEquals(first, second);
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM room_invites WHERE room_id = ?", String.class, roomId));
        assertEquals(second, jdbcTemplate.queryForObject(
                "SELECT invite_code FROM room_invites WHERE room_id = ?", String.class, roomId));
    }

    @Test
    void inviteCodeIsMultiUseAcrossApplicants() throws Exception {
        String code = createInvite();
        long secondApplicant = createUser("applicant2");

        // 같은 코드로 두 사람이 각각 입장 신청 — 코드가 소모되지 않는다(다회용).
        acceptWithCode(applicantId, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        acceptWithCode(secondApplicant, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM room_invites WHERE room_id = ?", String.class, roomId));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_join_requests WHERE room_id = ?", Integer.class, roomId));
    }

    @Test
    void canceledCodeIsRejectedThenReCreateReactivatesSameRow() throws Exception {
        String code = createInvite();
        long inviteId = jdbcTemplate.queryForObject(
                "SELECT id FROM room_invites WHERE room_id = ?", Long.class, roomId);

        // 코드 취소(만든 본인=host) → 취소된 코드로는 신청 불가(INVITE_EXPIRED).
        mockMvc.perform(delete("/api/v1/invites/{id}", inviteId).header("Authorization", bearer(hostId)))
                .andExpect(status().isOk());
        acceptWithCode(applicantId, code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVITE_EXPIRED"));

        // 재발급 → 같은 행이 ACTIVE로 부활(여전히 1행·id 불변), 새 코드로 신청 성공.
        String newCode = createInvite();
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_invites WHERE room_id = ?", Integer.class, roomId));
        assertEquals(inviteId, (long) jdbcTemplate.queryForObject(
                "SELECT id FROM room_invites WHERE room_id = ?", Long.class, roomId));
        acceptWithCode(applicantId, newCode).andExpect(status().isOk());
    }

    private String createInvite() throws Exception {
        String response = mockMvc.perform(post("/api/v1/rooms/{roomId}/invites", roomId)
                        .header("Authorization", bearer(hostId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInHours\":72}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.inviteCode");
    }

    private ResultActions acceptWithCode(long userId, String inviteCode) throws Exception {
        return mockMvc.perform(post("/api/v1/invites/accept")
                .header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteCode\":\"" + inviteCode + "\"}"));
    }

    private long createUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        String email = prefix + "-" + suffix + "@example.test";
        jdbcTemplate.update("INSERT INTO users (email, nickname, personal_invite_code) VALUES (?, ?, ?)",
                email, prefix, "CLV-" + suffix.substring(0, 6).toUpperCase());
        long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        userIds.add(userId);
        return userId;
    }

    private long createRoom() {
        jdbcTemplate.update("INSERT INTO friendship_rooms (name) VALUES (?)", "Invite test room " + UUID.randomUUID());
        long createdRoomId = jdbcTemplate.queryForObject(
                "SELECT id FROM friendship_rooms ORDER BY id DESC LIMIT 1", Long.class);
        roomIds.add(createdRoomId);
        return createdRoomId;
    }

    private void insertActiveMember(long targetRoomId, long userId) {
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id) VALUES (?, ?)", targetRoomId, userId);
    }

    private void insertLeftMember(long targetRoomId, long userId) {
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id, status, left_at) VALUES (?, ?, 'LEFT', ?)",
                targetRoomId, userId, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
    }

    private long insertPendingJoinRequest(long targetRoomId, long userId) {
        jdbcTemplate.update("INSERT INTO room_join_requests (room_id, user_id) VALUES (?, ?)", targetRoomId, userId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM room_join_requests WHERE room_id = ? AND user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, targetRoomId, userId);
    }

    private long insertAcceptedJoinRequest(long targetRoomId, long userId, long acceptedBy, LocalDateTime undoDeadlineAt) {
        jdbcTemplate.update("INSERT INTO room_join_requests (room_id, user_id, status, accepted_by, accepted_at, undo_deadline_at, version) "
                        + "VALUES (?, ?, 'ACCEPTED', ?, ?, ?, 1)",
                targetRoomId, userId, acceptedBy, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)), Timestamp.valueOf(undoDeadlineAt));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM room_join_requests WHERE room_id = ? AND user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, targetRoomId, userId);
    }

    private String bearer(long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId);
    }
}
