package com.korit.clovapi.domain.room.service;

import com.korit.clovapi.domain.room.entity.FriendshipExpLog;
import com.korit.clovapi.domain.room.entity.Room;
import com.korit.clovapi.domain.notification.service.NotificationService;
import com.korit.clovapi.domain.room.mapper.RoomMapper;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 우정 경험치 적립 중앙 처리(계약 §12).
 *
 * <p>모든 XP는 이 서비스를 거친다. 적립 = {@code friendship_exp_logs} 한 행 기록 +
 * {@code friendship_rooms}의 레벨·진행도 갱신이며, 둘은 항상 같은 트랜잭션에서 일어난다.
 *
 * <p>{@code exp_point}는 누적 총량이 아니라 <b>현재 레벨 안에서의 진행 XP</b>다. 누적 이력은
 * 로그 테이블로 본다.
 */
@Service
public class ExpService {

    /** 레벨 하나를 올리는 데 필요한 XP. 프로토타입 space.js의 levelProgress(0~100)와 같은 체감. */
    public static final int EXP_PER_LEVEL = 100;
    /** 만렙. 도달 후에는 XP를 적립하지 않는다. */
    public static final int MAX_LEVEL = 777;

    public static final String ACTION_MEMORY_WRITE = "MEMORY_WRITE";
    public static final String ACTION_MEMORY_IMAGE_BONUS = "MEMORY_IMAGE_BONUS";
    public static final String ACTION_PLAN_CREATE = "PLAN_CREATE";
    public static final String ACTION_PLAN_COMPLETE = "PLAN_COMPLETE";
    public static final String ACTION_MASCOT_INTERACT = "MASCOT_INTERACT";

    /** 추억 작성 기본 XP. */
    public static final int MEMORY_WRITE_BASE = 25;
    /** 사진 1장당 보너스. */
    public static final int MEMORY_IMAGE_BONUS_PER_IMAGE = 1;
    /** 추억 사진 보너스 상한(추억 1건당). */
    public static final int MEMORY_IMAGE_BONUS_MAX = 10;

    private final RoomMapper roomMapper;
    private final NotificationService notificationService;

    public ExpService(RoomMapper roomMapper, NotificationService notificationService) {
        this.roomMapper = roomMapper;
        this.notificationService = notificationService;
    }

    /**
     * XP를 적립하고 레벨업까지 처리한다. 만렙이거나 델타가 0 이하면 아무 일도 하지 않는다.
     *
     * <p>호출자(추억 작성·약속 완료 등)의 트랜잭션에 참여한다. 적립이 실패하면 유발 행위도 함께
     * 롤백되는 게 맞다 — 로그와 방 상태가 어긋나는 편이 더 위험하다.
     *
     * @param referenceId 유발 리소스 id(plan/memory). 마스코트처럼 없으면 null
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void grant(long roomId, long userId, String actionType, int expDelta, Long referenceId) {
        if (expDelta <= 0) {
            return;
        }
        // 레벨업 판정은 읽고-쓰기라 동시 적립 시 한쪽이 유실될 수 있다 → 행을 잠그고 읽는다.
        Room room = roomMapper.findByIdForUpdate(roomId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        int level = room.getFriendshipLevel() == null ? 1 : room.getFriendshipLevel();
        if (level >= MAX_LEVEL) {
            return;
        }

        FriendshipExpLog log = new FriendshipExpLog();
        log.setRoomId(roomId);
        log.setTriggeredBy(userId);
        log.setActionType(actionType);
        log.setExpDelta(expDelta);
        log.setReferenceId(referenceId);
        roomMapper.insertExpLog(log);

        int startLevel = level;
        int exp = (room.getExpPoint() == null ? 0 : room.getExpPoint()) + expDelta;
        // 한 번에 큰 XP가 들어와 100을 여러 번 넘기면 넘긴 만큼 연속으로 올린다.
        while (exp >= EXP_PER_LEVEL && level < MAX_LEVEL) {
            level++;
            exp -= EXP_PER_LEVEL;
        }
        if (level >= MAX_LEVEL) {
            level = MAX_LEVEL;
            exp = 0; // 만렙은 진행도를 남기지 않는다.
        }
        roomMapper.updateLevelAndExp(roomId, level, exp);

        // 레벨이 실제로 올랐을 때만 방 전체에 알림(계약 §13). 연속 레벨업이어도 최종 레벨 하나만.
        // actor=null(방 전체 이벤트), payload.level=도달 레벨.
        if (level > startLevel) {
            notificationService.fanOut(roomId, null, NotificationService.TYPE_FRIEND,
                    NotificationService.SUB_LEVEL_UP, roomId, "{\"level\":" + level + "}");
        }
    }

    /**
     * 추억 사진 보너스(장당 +1, 추억당 최대 10). 프로덕션은 추억 생성 후 이미지를 따로 커밋하므로
     * 작성 시점에 사진 수를 알 수 없다 → 커밋될 때마다 증분 적립한다(계약 §12).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void grantMemoryImageBonus(long roomId, long userId, long memoryId) {
        int already = roomMapper.sumExpDeltaByReference(roomId, ACTION_MEMORY_IMAGE_BONUS, memoryId);
        if (already >= MEMORY_IMAGE_BONUS_MAX) {
            return;
        }
        grant(roomId, userId, ACTION_MEMORY_IMAGE_BONUS, MEMORY_IMAGE_BONUS_PER_IMAGE, memoryId);
    }

    /**
     * 추억 작성 XP = 기본 25 + 글자 보너스(100자↑ +10 / 50자↑ +5, 최고 구간 하나만).
     * 사진 보너스는 여기 포함되지 않는다({@link #grantMemoryImageBonus}).
     */
    public static int memoryWriteExp(String content) {
        int length = content == null ? 0 : content.trim().length();
        int bonus = length >= 100 ? 10 : length >= 50 ? 5 : 0;
        return MEMORY_WRITE_BASE + bonus;
    }
}
