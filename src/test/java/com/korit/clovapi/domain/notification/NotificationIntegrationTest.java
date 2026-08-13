package com.korit.clovapi.domain.notification;

import com.korit.clovapi.global.security.jwt.JwtTokenProvider;
import com.korit.clovapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private long roomId;
    private long memberId;
    private long outsiderId;
    private long notificationId1;
    private long notificationId2;

    @BeforeEach
    void setUp() {
        roomId = insertRoom();
        memberId = insertUser("member");
        outsiderId = insertUser("outsider");
        insertMember(roomId, memberId);

        notificationId1 = insertNotification(roomId, memberId, "NOTICE");
        notificationId2 = insertNotification(roomId, memberId, "FRIEND");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notifications WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", roomId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", memberId, outsiderId);
    }

    @Test
    void getNotificationsWithFilter() throws Exception {
        // Without filter
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].actor.nickname").value("member"));

        // With type filter
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/notifications?type=NOTICE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("NOTICE"))
                .andExpect(jsonPath("$.data.items[0].actor.nickname").value("member"));

        // Non-member approach
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROOM_MEMBER_NOT_FOUND"));
    }

    @Test
    void markAsRead() throws Exception {
        // Successful read
        mockMvc.perform(patch("/api/v1/notifications/" + notificationId1 + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk());

        // Check if actually read
        Boolean isRead = jdbcTemplate.queryForObject(
                "SELECT is_read FROM notifications WHERE id = ?", Boolean.class, notificationId1);
        assert Boolean.TRUE.equals(isRead);

        // Others trying to read
        mockMvc.perform(patch("/api/v1/notifications/" + notificationId2 + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void markAllAsRead() throws Exception {
        mockMvc.perform(patch("/api/v1/rooms/" + roomId + "/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(2));

        // Check if actually all read
        Long unreadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE room_id = ? AND recipient_id = ? AND is_read = false",
                Long.class, roomId, memberId);
        assert unreadCount != null && unreadCount == 0;

        // Non-member approach
        mockMvc.perform(patch("/api/v1/rooms/" + roomId + "/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROOM_MEMBER_NOT_FOUND"));
    }

    // web-design-repository#89 — 종 아이콘 배지는 room 무관 유저 전체 기준이어야 한다.
    // 다른 방(roomId 안 넘김)의 안읽음도 true로 잡히는지, 전부 읽으면 false로 꺼지는지 확인.
    @Test
    void unreadIsRoomAgnosticAndClearsAfterReadAll() throws Exception {
        // setUp에서 memberId 앞으로 이 방에 안읽음 2건이 이미 있다.
        mockMvc.perform(get("/api/v1/users/me/notifications/unread")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUnread").value(true));

        // 알림이 하나도 없는 유저는 false.
        mockMvc.perform(get("/api/v1/users/me/notifications/unread")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUnread").value(false));

        // 이 방을 전부 읽음 처리해도, 다른 방에 안읽음이 남아 있으면 여전히 true여야 한다(room 무관).
        long otherRoomId = insertRoom();
        insertMember(otherRoomId, memberId);
        insertNotification(otherRoomId, memberId, "FRIEND");
        try {
            mockMvc.perform(patch("/api/v1/rooms/" + roomId + "/notifications/read-all")
                            .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/users/me/notifications/unread")
                            .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.hasUnread").value(true));

            // 다른 방도 마저 읽으면 그제야 false.
            mockMvc.perform(patch("/api/v1/rooms/" + otherRoomId + "/notifications/read-all")
                            .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/users/me/notifications/unread")
                            .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.hasUnread").value(false));
        } finally {
            jdbcTemplate.update("DELETE FROM notifications WHERE room_id = ?", otherRoomId);
            jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", otherRoomId);
            jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", otherRoomId);
        }
    }

    // clov-api#174 — 방 안 배지는 이 방만 기준이어야 한다(위 unreadIsRoomAgnostic 테스트와 대비).
    @Test
    void unreadInRoomIsRoomScoped() throws Exception {
        // setUp에서 memberId 앞으로 이 방에 안읽음 2건이 이미 있다.
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/notifications/unread")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUnread").value(true));

        long otherRoomId = insertRoom();
        insertMember(otherRoomId, memberId);
        insertNotification(otherRoomId, memberId, "FRIEND");
        try {
            // 이 방(roomId)만 읽어도 다른 방(otherRoomId)의 배지엔 영향 없다.
            mockMvc.perform(patch("/api/v1/rooms/" + roomId + "/notifications/read-all")
                            .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/rooms/" + roomId + "/notifications/unread")
                            .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.hasUnread").value(false));
            // 다른 방은 안읽음이 그대로 남아 있어야 한다 — 전체 기준(existsUnread)과 달리 여기서 안 꺼진다.
            mockMvc.perform(get("/api/v1/rooms/" + otherRoomId + "/notifications/unread")
                            .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.hasUnread").value(true));
        } finally {
            jdbcTemplate.update("DELETE FROM notifications WHERE room_id = ?", otherRoomId);
            jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", otherRoomId);
            jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", otherRoomId);
        }

        // 비멤버는 다른 room-scoped 엔드포인트와 동일하게 403.
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/notifications/unread")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROOM_MEMBER_NOT_FOUND"));
    }

    private String bearer(long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId);
    }

    private long insertRoom() {
        jdbcTemplate.update("INSERT INTO friendship_rooms (name) VALUES (?)", "noti-it-" + UUID.randomUUID());
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertUser(String label) {
        String email = "noti-it-" + label + "-" + UUID.randomUUID() + "@example.test";
        jdbcTemplate.update(
                "INSERT INTO users (email, nickname) VALUES (?, ?)",
                email, label
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertMember(long roomId, long userId) {
        jdbcTemplate.update(
                "INSERT INTO room_members (room_id, user_id, status) VALUES (?, ?, 'ACTIVE')",
                roomId, userId
        );
    }

    private long insertNotification(long roomId, long recipientId, String type) {
        // sub_type은 NOT NULL(계약 §13). 필터 테스트라 type에 맞는 임의 유효값이면 충분하다.
        String subType = "NOTICE".equals(type) ? "ADMIN_NOTICE" : "ROOM_UPDATE";
        jdbcTemplate.update(
                "INSERT INTO notifications (room_id, recipient_id, actor_id, type, sub_type, reference_id, is_read, created_at) VALUES (?, ?, ?, ?, ?, ?, false, NOW())",
                roomId, recipientId, recipientId, type, subType, (Long) null
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
