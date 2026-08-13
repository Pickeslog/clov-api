package com.korit.clovapi.domain.letter;

import com.jayway.jsonpath.JsonPath;
import com.korit.clovapi.global.security.jwt.JwtTokenProvider;
import com.korit.clovapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LetterIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private long roomId;
    private long senderId;
    private long receiverId;
    private long outsiderId;

    @BeforeEach
    void setUp() {
        roomId = insertRoom();
        senderId = insertUser("sender");
        receiverId = insertUser("receiver");
        outsiderId = insertUser("outsider");
        insertMember(roomId, senderId);
        insertMember(roomId, receiverId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM letter_favorites WHERE letter_id IN (SELECT id FROM lucky_letters WHERE room_id = ?)", roomId);
        jdbcTemplate.update("DELETE FROM lucky_letters WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM notifications WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", roomId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?)", senderId, receiverId, outsiderId);
    }

    @Test
    void sendsReadsAndFavoritesALetterFollowingTheContract() throws Exception {
        MvcResult sendResult = mockMvc.perform(post("/api/v1/rooms/" + roomId + "/letters")
                        .header(HttpHeaders.AUTHORIZATION, bearer(senderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverUserId\":\"" + receiverId + "\",\"content\":\"고마웠어\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.sender.id").value(String.valueOf(senderId)))
                .andExpect(jsonPath("$.data.receiver.id").value(String.valueOf(receiverId)))
                .andExpect(jsonPath("$.data.emoji").value("💌"))
                .andExpect(jsonPath("$.data.isFavorite").value(false))
                .andReturn();

        String letterId = JsonPath.read(sendResult.getResponse().getContentAsString(), "$.data.id");

        // #163 — 편지 수신 알림. 수신자 1명에게만(팬아웃 아님), referenceId는 letterId.
        java.util.Map<String, Object> notification = jdbcTemplate.queryForMap(
                "SELECT recipient_id, actor_id, type, sub_type, reference_id FROM notifications WHERE room_id = ?",
                roomId);
        assert ((Number) notification.get("recipient_id")).longValue() == receiverId;
        assert ((Number) notification.get("actor_id")).longValue() == senderId;
        assert "FRIEND".equals(notification.get("type"));
        assert "LETTER_RECEIVE".equals(notification.get("sub_type"));
        assert String.valueOf(notification.get("reference_id")).equals(letterId);

        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/letters?box=sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(senderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(letterId))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/letters?box=received")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROOM_MEMBER_NOT_FOUND"));

        mockMvc.perform(patch("/api/v1/letters/" + letterId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(senderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(patch("/api/v1/letters/" + letterId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").isString());

        mockMvc.perform(patch("/api/v1/letters/" + letterId + "/favorite")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(patch("/api/v1/letters/" + letterId + "/favorite")
                        .header(HttpHeaders.AUTHORIZATION, bearer(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFavorite").value(true));

        mockMvc.perform(patch("/api/v1/letters/" + letterId + "/favorite")
                        .header(HttpHeaders.AUTHORIZATION, bearer(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFavorite").value(false));
    }

    @Test
    void sendsAndReadsALetterWithATitle() throws Exception {
        MvcResult sendResult = mockMvc.perform(post("/api/v1/rooms/" + roomId + "/letters")
                        .header(HttpHeaders.AUTHORIZATION, bearer(senderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverUserId\":\"" + receiverId + "\",\"title\":\"생일 축하해\",\"content\":\"항상 응원할게\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("생일 축하해"))
                .andReturn();

        String letterId = JsonPath.read(sendResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/letters?box=sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(senderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(letterId))
                .andExpect(jsonPath("$.data.items[0].title").value("생일 축하해"));
    }

    @Test
    void omitsTitleFieldWhenNotProvided() throws Exception {
        // 제목 없이 보내면 응답에서 title 필드 자체가 생략된다(NON_NULL) — "" 취급이 아니라
        // 아예 없다는 걸 명시적으로 검증한다.
        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/letters")
                        .header(HttpHeaders.AUTHORIZATION, bearer(senderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverUserId\":\"" + receiverId + "\",\"content\":\"제목 없이\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").doesNotExist());
    }

    @Test
    void requiresExactlyOneOfReceiverOrBroadcast() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/letters")
                        .header(HttpHeaders.AUTHORIZATION, bearer(senderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverUserId\":\"" + receiverId + "\",\"broadcast\":true,\"content\":\"둘다\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/letters")
                        .header(HttpHeaders.AUTHORIZATION, bearer(senderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"아무도 없음\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void broadcastsToActiveMembersExcludingSender() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/letters")
                        .header(HttpHeaders.AUTHORIZATION, bearer(senderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"broadcast\":true,\"content\":\"다들 고마워\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sentCount").value(1));

        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/letters?box=received")
                        .header(HttpHeaders.AUTHORIZATION, bearer(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        // #163 — 브로드캐스트도 알림이 나가야 한다. 발신자는 제외, referenceId는 편지가
        // 여러 건일 수 있어 null(LEVEL_UP과 같은 처리).
        java.util.Map<String, Object> notification = jdbcTemplate.queryForMap(
                "SELECT recipient_id, actor_id, type, sub_type, reference_id FROM notifications WHERE room_id = ?",
                roomId);
        assert ((Number) notification.get("recipient_id")).longValue() == receiverId;
        assert ((Number) notification.get("actor_id")).longValue() == senderId;
        assert "LETTER_RECEIVE".equals(notification.get("sub_type"));
        assert notification.get("reference_id") == null;
    }

    // 활성 멤버가 발신자뿐이면 편지도 알림도 생성되면 안 된다(기존 receiverIds.isEmpty() 가드).
    @Test
    void broadcastWithNoOtherActiveMembersCreatesNeitherLetterNorNotification() throws Exception {
        long soloRoomId = insertRoom();
        long soloSenderId = insertUser("solo-sender");
        insertMember(soloRoomId, soloSenderId);

        try {
            mockMvc.perform(post("/api/v1/rooms/" + soloRoomId + "/letters")
                            .header(HttpHeaders.AUTHORIZATION, bearer(soloSenderId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"broadcast\":true,\"content\":\"아무도 없네\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.sentCount").value(0));

            Long letterCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM lucky_letters WHERE room_id = ?", Long.class, soloRoomId);
            Long notificationCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE room_id = ?", Long.class, soloRoomId);
            assert letterCount != null && letterCount == 0;
            assert notificationCount != null && notificationCount == 0;
        } finally {
            jdbcTemplate.update("DELETE FROM notifications WHERE room_id = ?", soloRoomId);
            jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", soloRoomId);
            jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", soloRoomId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", soloSenderId);
        }
    }

    private String bearer(long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId);
    }

    private long insertRoom() {
        jdbcTemplate.update("INSERT INTO friendship_rooms (name) VALUES (?)", "letter-it-" + UUID.randomUUID());
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertUser(String label) {
        String email = "letter-it-" + label + "-" + UUID.randomUUID() + "@example.test";
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
}
