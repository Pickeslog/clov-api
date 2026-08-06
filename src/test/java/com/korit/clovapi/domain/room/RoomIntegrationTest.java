package com.korit.clovapi.domain.room;

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
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String accessToken;
    private long userId;
    private Long roomId;
    private final List<Long> roomIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        AuthUser user = signUp("Room Test");
        accessToken = user.accessToken();
        userId = user.userId();
    }

    @AfterEach
    void cleanUp() {
        for (Long createdRoomId : roomIds) {
            jdbcTemplate.update("DELETE FROM friendship_exp_logs WHERE room_id = ?", createdRoomId);
            jdbcTemplate.update("DELETE FROM notifications WHERE room_id = ?", createdRoomId);
            jdbcTemplate.update("DELETE FROM plans WHERE room_id = ?", createdRoomId);
            jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", createdRoomId);
            jdbcTemplate.update("DELETE FROM friendship_rooms WHERE id = ?", createdRoomId);
        }
        for (Long createdUserId : userIds) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", createdUserId);
            // 마스코트 교감이 골드도 지급하면서(§15-4) 이 테스트 유저들도 user_wallets 행을 갖게 됐다 —
            // 지우지 않으면 users DELETE가 FK(fk_wallet_transactions_user/fk_user_wallets_user)에 걸린다.
            jdbcTemplate.update("DELETE FROM wallet_transactions WHERE user_id = ?", createdUserId);
            jdbcTemplate.update("DELETE FROM user_wallets WHERE user_id = ?", createdUserId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", createdUserId);
        }
    }

    @Test
    void findMyRoomsReturnsOnlyActiveMembershipsInFavoriteOrder() throws Exception {
        long regularRoomId = createRoom(accessToken, "Regular Room");
        long favoriteRoomId = createRoom(accessToken, "Favorite Room");

        mockMvc.perform(patch("/api/v1/rooms/{roomId}/favorite", favoriteRoomId)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isFavorite\":true}"))
                .andExpect(status().isOk());

        AuthUser otherUser = signUp("Other Room Test");
        createRoom(otherUser.accessToken(), "Non Member Room");
        long leftMemberRoomId = createRoom(otherUser.accessToken(), "Left Member Room");
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id, status) VALUES (?, ?, 'LEFT')",
                leftMemberRoomId, userId);

        mockMvc.perform(get("/api/v1/rooms").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(favoriteRoomId)))
                .andExpect(jsonPath("$.data.items[0].name").value("Favorite Room"))
                .andExpect(jsonPath("$.data.items[0].themeColor").value("#7CC6A6"))
                .andExpect(jsonPath("$.data.items[0].coverPhotoUrl").value("https://example.test/cover.jpg"))
                .andExpect(jsonPath("$.data.items[0].friendshipLevel").value(1))
                .andExpect(jsonPath("$.data.items[0].memberCount").value(1))
                .andExpect(jsonPath("$.data.items[0].isFavorite").value(true))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].createdAt").exists())
                .andExpect(jsonPath("$.data.items[1].id").value(String.valueOf(regularRoomId)))
                .andExpect(jsonPath("$.data.items[1].isFavorite").value(false));
    }

    @Test
    void findMyRoomsIncludesNextScheduledPlan() throws Exception {
        long tripRoom = createRoom(accessToken, "Trip Room");
        createRoom(accessToken, "Empty Room");
        jdbcTemplate.update(
                "INSERT INTO plans (room_id, writer_id, title, plan_date, status, memory_status) "
                        + "VALUES (?, ?, '제주 여행', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'SCHEDULED', 'NONE')",
                tripRoom, userId);

        mockMvc.perform(get("/api/v1/rooms").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.name=='Trip Room')].nextPlan.title",
                        org.hamcrest.Matchers.hasItem("제주 여행")))
                .andExpect(jsonPath("$.data.items[?(@.name=='Trip Room')].nextPlan.planDate").exists())
                // 약속 없는 방은 nextPlan = null → 필터 결과에 title 없음.
                .andExpect(jsonPath("$.data.items[?(@.name=='Empty Room')].nextPlan.title").isEmpty());
    }

    @Test
    void findMyRoomsReturnsAnEmptyItemsArrayWhenTheUserHasNoActiveRooms() throws Exception {
        mockMvc.perform(get("/api/v1/rooms").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void findMyRoomsMemberAvatarsIncludeOnlyActiveMembersOrderedByJoinedAt() throws Exception {
        long avatarRoom = createRoom(accessToken, "Avatar Room");
        AuthUser second = signUp("Second Member");
        AuthUser leftUser = signUp("Left Member");
        // 뒤에 참여한 멤버가 joinedAt 오름차순에서 두 번째로 와야 한다.
        jdbcTemplate.update(
                "INSERT INTO room_members (room_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', DATE_ADD(NOW(), INTERVAL 1 MINUTE))",
                avatarRoom, second.userId());
        // LEFT 멤버는 memberAvatars에 보이면 안 된다(계약 §4-3, clov-api#141).
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id, status) VALUES (?, ?, 'LEFT')",
                avatarRoom, leftUser.userId());

        mockMvc.perform(get("/api/v1/rooms").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].memberAvatars.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].memberAvatars[0].userId").value(String.valueOf(userId)))
                .andExpect(jsonPath("$.data.items[0].memberAvatars[0].nickname").value("Room Test"))
                .andExpect(jsonPath("$.data.items[0].memberAvatars[1].userId").value(String.valueOf(second.userId())))
                .andExpect(jsonPath("$.data.items[0].memberAvatars[1].nickname").value("Second Member"));
    }

    @Test
    void findMyRoomsMemberAvatarsAreGroupedIndependentlyPerRoom() throws Exception {
        // 배치(IN 절) 조회가 방마다 결과를 뒤섞지 않는지 확인한다(clov-api#141 — N+1을 없애면서
        // 서버 안에서 새로 N+1을 만들지 않는 것과 별개로, 그룹핑 자체가 틀리면 다른 방의 아바타가 섞인다).
        long roomWithExtraMember = createRoom(accessToken, "Extra Member Room");
        long roomAlone = createRoom(accessToken, "Room Alone");
        AuthUser second = signUp("Extra Member");
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id, status) VALUES (?, ?, 'ACTIVE')",
                roomWithExtraMember, second.userId());

        mockMvc.perform(get("/api/v1/rooms").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.name=='Extra Member Room')].memberAvatars[*].userId",
                        org.hamcrest.Matchers.hasItems(String.valueOf(userId), String.valueOf(second.userId()))))
                .andExpect(jsonPath("$.data.items[?(@.name=='Room Alone')].memberAvatars[*].userId",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(String.valueOf(second.userId())))));
    }

    @Test
    void coverImagePresignReturnsSignedPutUrlForMember() throws Exception {
        long createdRoomId = createRoom(accessToken, "Cover Room");
        mockMvc.perform(post("/api/v1/rooms/{roomId}/cover-image/presign", createdRoomId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"fileSize\":102400}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").value(org.hamcrest.Matchers.startsWith("https://")))
                .andExpect(jsonPath("$.data.uploadUrl").value(org.hamcrest.Matchers.containsString("X-Amz-Signature")))
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.containsString("rooms/" + createdRoomId + "/cover/")))
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.endsWith(".png")))
                .andExpect(jsonPath("$.data.expiresIn").value(300));
    }

    @Test
    void coverImagePresignRejectsNonMember() throws Exception {
        long createdRoomId = createRoom(accessToken, "Cover Room");
        AuthUser outsider = signUp("Outsider");
        mockMvc.perform(post("/api/v1/rooms/{roomId}/cover-image/presign", createdRoomId)
                        .header("Authorization", "Bearer " + outsider.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"fileSize\":102400}"))
                .andExpect(status().isForbidden());
    }

    private AuthUser signUp(String nickname) throws Exception {
        String email = "room-it-" + UUID.randomUUID() + "@example.test";
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Abcd1234!\","
                                + "\"nickname\":\"" + nickname + "\","
                                + "\"agreements\":{\"service\":true,\"privacy\":true,\"marketing\":false}}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = JsonPath.read(signup.getResponse().getContentAsString(), "$.data.accessToken");
        long createdUserId = Long.parseLong(JsonPath.read(signup.getResponse().getContentAsString(), "$.data.user.id"));
        userIds.add(createdUserId);
        return new AuthUser(createdUserId, token);
    }

    private long createRoom(String token, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"description\":\"Prepare together\","
                                + "\"themeColor\":\"#7CC6A6\",\"transportType\":\"airplane\","
                                + "\"coverPhotoUrl\":\"https://example.test/cover.jpg\",\"coverTitle\":\"Room\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long createdRoomId = Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
        roomIds.add(createdRoomId);
        return createdRoomId;
    }

    @Test
    void roomNameLengthIsEnforcedOnCreateAndUpdate() throws Exception {
        // 계약 §6: 앞뒤 공백 제거 후 2~20자.
        createRoomNamed("가").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        createRoomNamed("가".repeat(21)).andExpect(status().isBadRequest());
        // 공백으로 부풀린 이름도 막힌다 — trim 뒤 1자. trim이 없으면 @Size가 5자로 보고 통과한다.
        createRoomNamed("    가    ").andExpect(status().isBadRequest());

        // 편집 경로도 같은 제약을 받는다.
        long createdRoomId = createRoom(accessToken, "Rename Room");
        mockMvc.perform(patch("/api/v1/rooms/{roomId}", createdRoomId)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "가".repeat(21) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roomNameIsTrimmedAndAllowsPunctuationAndEmoji() throws Exception {
        // 저장되는 값도 trim된 값이어야 한다.
        MvcResult padded = createRoomNamed("  제주 가자  ").andExpect(status().isCreated()).andReturn();
        long paddedRoomId = Long.parseLong(JsonPath.read(padded.getResponse().getContentAsString(), "$.data.id"));
        roomIds.add(paddedRoomId);
        assertEquals("제주 가자", jdbcTemplate.queryForObject(
                "SELECT name FROM friendship_rooms WHERE id = ?", String.class, paddedRoomId));

        // 문자 종류는 제한하지 않는다(계약 §6) — 목업 정규식을 그대로 가져오면 이 이름들이 거부된다.
        // 나중에 누가 @Pattern을 넣으면 여기서 잡히도록 남겨둔 회귀 방지 테스트다.
        MvcResult fancy = createRoomNamed("제주 가자! 🏝️").andExpect(status().isCreated()).andReturn();
        long fancyRoomId = Long.parseLong(JsonPath.read(fancy.getResponse().getContentAsString(), "$.data.id"));
        roomIds.add(fancyRoomId);
        assertEquals("제주 가자! 🏝️", jdbcTemplate.queryForObject(
                "SELECT name FROM friendship_rooms WHERE id = ?", String.class, fancyRoomId));
    }

    private ResultActions createRoomNamed(String name) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"));
    }

    @Test
    void roomLifecycleAndMascotEndpointsFollowTheContract() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jeju Trip\",\"description\":\"Prepare together\","
                                + "\"themeColor\":\"#7CC6A6\",\"transportType\":\"airplane\","
                                + "\"coverPhotoUrl\":\"https://example.test/cover.jpg\",\"coverTitle\":\"Jeju\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.memberCount").value(1))
                .andReturn();
        roomId = Long.parseLong(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
        roomIds.add(roomId);

        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Jeju Trip"));

        mockMvc.perform(patch("/api/v1/rooms/{roomId}", roomId)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Updated\",\"coverPhotoUrl\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Updated"))
                .andExpect(jsonPath("$.data.coverPhotoUrl").doesNotExist());
        // 방 수정 알림은 수정자 본인을 제외한 ACTIVE 멤버에게만 간다(계약 §13, FRIEND/ROOM_UPDATE).
        // 이 방은 멤버가 수정자 한 명뿐이라 알림이 생기지 않는다. (팬아웃+본인제외 검증은 NotificationFanOutIntegrationTest)
        org.junit.jupiter.api.Assertions.assertEquals(0,
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE room_id = ?", Integer.class, roomId));

        mockMvc.perform(get("/api/v1/rooms/{roomId}/members", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].membershipId").isString())
                .andExpect(jsonPath("$.data.items[0].userId").isString())
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"));

        mockMvc.perform(patch("/api/v1/rooms/{roomId}/members/me/status-message", roomId)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusMessage\":\"Packing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusMessage").value("Packing"));

        mockMvc.perform(patch("/api/v1/rooms/{roomId}/favorite", roomId)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isFavorite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFavorite").value(true));

        // 교감 캡은 10회(계약 §12) — §15-4의 EARN_MASCOT 횟수와 같은 값이어야 한다.
        // 매 회차 earnedGold 200이 나오는 것까지 본다: 하루 총 상한(6,000) 안이라 10회 전부
        // 온전히 지급된다(10 × 200 = 2,000).
        for (int index = 0; index < 10; index++) {
            mockMvc.perform(post("/api/v1/rooms/{roomId}/mascot/interact", roomId)
                            .header("Authorization", bearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.expDelta").value(2))
                    .andExpect(jsonPath("$.data.earnedGold").value(200))
                    .andExpect(jsonPath("$.data.remainingToday").value(9 - index));
        }
        mockMvc.perform(post("/api/v1/rooms/{roomId}/mascot/interact", roomId)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("MASCOT_INTERACTION_LIMIT_REACHED"));

        mockMvc.perform(get("/api/v1/rooms/{roomId}/exp-logs", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.items[0].id").isString());

        mockMvc.perform(get("/api/v1/rooms/{roomId}/level", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friendshipLevel").value(1))
                // 교감 10회 × MASCOT_INTERACT 2 XP = 20 (캡을 3 → 10으로 올린 값을 따라간다)
                .andExpect(jsonPath("$.data.expPoint").value(20))
                .andExpect(jsonPath("$.data.expForNextLevel").value(100))
                .andExpect(jsonPath("$.data.remainingToNextLevel").value(80));

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/members/me", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/revive", roomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.scheduledDeleteAt").doesNotExist());
    }

    @Test
    void leavingRoomNotifiesRemainingMembersButNotTheLeaver() throws Exception {
        // 계약 §13 MEMBER_LEFT(web-design-repository#50): 수신자=남은 멤버 전원(나간 사람 제외), actor=나간 사람.
        long leaveRoomId = createRoom(accessToken, "Leave Notify Room");
        AuthUser remainingMember = signUp("Remaining Member");
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id) VALUES (?, ?)", leaveRoomId, remainingMember.userId());

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/members/me", leaveRoomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk());

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE room_id = ?", Integer.class, leaveRoomId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE room_id = ? AND recipient_id = ? AND actor_id = ? "
                        + "AND type = 'FRIEND' AND sub_type = 'MEMBER_LEFT' AND reference_id = ?",
                Integer.class, leaveRoomId, remainingMember.userId(), userId, leaveRoomId));
    }

    @Test
    void membersResponseExposesZeroPaddedBirthMonthDayAndNullWhenMissing() throws Exception {
        long localRoomId = createRoom(accessToken, "Birthday Room");

        AuthUser withBirthdate = signUp("Birthday Member");
        jdbcTemplate.update("UPDATE users SET birthdate = ? WHERE id = ?",
                java.sql.Date.valueOf("2000-01-05"), withBirthdate.userId());
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id, status) VALUES (?, ?, 'ACTIVE')",
                localRoomId, withBirthdate.userId());

        // 탈퇴(익명화) 계정 — anonymize()는 nickname만 지우고 birthdate는 남기므로, 쿼리가 직접 숨겨야 한다.
        AuthUser anonymized = signUp("Anonymized Member");
        jdbcTemplate.update("UPDATE users SET birthdate = ?, is_anonymized = true WHERE id = ?",
                java.sql.Date.valueOf("1999-12-25"), anonymized.userId());
        jdbcTemplate.update("INSERT INTO room_members (room_id, user_id, status) VALUES (?, ?, 'ACTIVE')",
                localRoomId, anonymized.userId());

        mockMvc.perform(get("/api/v1/rooms/{roomId}/members", localRoomId).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                // room creator never had birthdate set → null, not omitted or NaN
                .andExpect(jsonPath("$.data.items[?(@.userId=='" + userId + "')].birthMonthDay").value(
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                // zero-padded month and day (Jan 5th → "01-05", not "1-5")
                .andExpect(jsonPath("$.data.items[?(@.userId=='" + withBirthdate.userId() + "')].birthMonthDay").value(
                        org.hamcrest.Matchers.contains("01-05")))
                // anonymized account has a birthdate in the DB but must still report null
                .andExpect(jsonPath("$.data.items[?(@.userId=='" + anonymized.userId() + "')].birthMonthDay").value(
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    // 마스코트 하루 10회 한도의 "하루"는 KST 자정에 리셋된다(#66).
    // UTC 자정으로 자르면 한국 사용자에게는 오전 9시에 리셋돼, 자정을 넘겨 날짜가 바뀌어도 한도가 그대로 소진 상태로 남는다.
    @Test
    void mascotDailyLimitResetsAtKoreaMidnightNotUtcMidnight() throws Exception {
        long mascotRoomId = createRoom(accessToken, "Mascot Limit Room");
        // 구현과 별개로 여기서 다시 계산한다 — 기준 지역이 바뀌면 테스트가 알아채야 하기 때문.
        ZoneId korea = ZoneId.of("Asia/Seoul");
        LocalDateTime startOfTodayUtc = LocalDate.now(korea).atStartOfDay(korea)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        // 어제(KST) 23:59 — 오늘 창 밖이라 한도에 잡히면 안 된다.
        insertMascotLog(mascotRoomId, startOfTodayUtc.minusMinutes(1));
        // 오늘(KST) 00:01부터 9건 — UTC 자정으로 자르면 이들이 "어제"로 새어나가 한도가 잘못 리셋된다.
        for (int minute = 1; minute <= 9; minute++) {
            insertMascotLog(mascotRoomId, startOfTodayUtc.plusMinutes(minute));
        }

        // 오늘 9회 썼으므로 이번 1회는 성공하고 남은 횟수는 0이어야 한다.
        mockMvc.perform(post("/api/v1/rooms/{roomId}/mascot/interact", mascotRoomId)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingToday").value(0));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/mascot/interact", mascotRoomId)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("MASCOT_INTERACTION_LIMIT_REACHED"));
    }

    private void insertMascotLog(long targetRoomId, LocalDateTime createdAtUtc) {
        // created_at은 LocalDateTime 그대로 넘긴다 — Timestamp로 넘기면 드라이버가 JVM 기본 시간대로
        // 환산해 경계 테스트가 9시간 틀어질 수 있다.
        jdbcTemplate.update("INSERT INTO friendship_exp_logs (room_id, triggered_by, action_type, exp_delta, created_at)"
                        + " VALUES (?, ?, 'MASCOT_INTERACT', 2, ?)",
                targetRoomId, userId, createdAtUtc);
        // 넣은 값이 환산 없이 그대로 저장됐는지 확인한다. 이게 어긋나면 아래 단언들이 엉뚱한 이유로 실패한다.
        org.junit.jupiter.api.Assertions.assertEquals(createdAtUtc, jdbcTemplate.queryForObject(
                "SELECT created_at FROM friendship_exp_logs WHERE room_id = ? ORDER BY id DESC LIMIT 1",
                LocalDateTime.class, targetRoomId));
    }

    private String bearerToken() {
        return "Bearer " + accessToken;
    }

    private record AuthUser(long userId, String accessToken) {
    }
}
