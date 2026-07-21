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
                .andExpect(jsonPath("$.data.items.length()").value(2));

        // With type filter
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/notifications?type=NOTICE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("NOTICE"));

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

    private String bearer(long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId);
    }

    private long insertRoom() {
        jdbcTemplate.update("INSERT INTO friendship_rooms (name) VALUES (?)", "noti-it-" + UUID.randomUUID());
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertUser(String label) {
        String email = "noti-it-" + label + "-" + UUID.randomUUID() + "@example.test";
        String inviteCode = "CLV-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbcTemplate.update(
                "INSERT INTO users (email, nickname, personal_invite_code) VALUES (?, ?, ?)",
                email, label, inviteCode
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
        jdbcTemplate.update(
                "INSERT INTO notifications (room_id, recipient_id, actor_id, type, reference_id, is_read, created_at) VALUES (?, ?, ?, ?, ?, false, NOW())",
                roomId, recipientId, recipientId, type, (Long) null
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
