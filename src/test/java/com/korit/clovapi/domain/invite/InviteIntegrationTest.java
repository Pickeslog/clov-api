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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
