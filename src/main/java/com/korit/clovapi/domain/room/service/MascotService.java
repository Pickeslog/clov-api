package com.korit.clovapi.domain.room.service;

import com.korit.clovapi.domain.room.dto.MascotInteractionResponse;
import com.korit.clovapi.domain.room.dto.RoomLevelResponse;
import com.korit.clovapi.domain.room.entity.Room;
import com.korit.clovapi.domain.room.mapper.RoomMapper;
import com.korit.clovapi.domain.shop.entity.WalletTransaction;
import com.korit.clovapi.domain.shop.service.ShopService;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import com.korit.clovapi.global.time.ClovTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MascotService {

    /**
     * 교감 횟수 캡(계약 §12) — **방 단위**로 센다. 초과하면 429.
     *
     * ★ 아래 DAILY_GOLD_LIMIT 와 같은 값이어야 한다. 여기가 더 작으면 골드 캡의 초과분이
     * 영원히 도달할 수 없는 죽은 값이 된다 — 실제로 2026-08-04에 §15-4만 10으로 올리고
     * 여기를 3으로 둬서, 하루 2,000골드가 방 4개 이상 있어야 닿는 값이 됐다.
     */
    private static final int DAILY_INTERACTION_LIMIT = 10;
    private static final int INTERACTION_EXP = 2;
    // 마스코트 교감 1회당 지급 골드(계약 §15-4). 골드 캡은 위 교감 캡(방 단위)과 별개로
    // **유저 단위**로 센다 — 방 단위로 세면 방 개수만큼 골드도 늘어나 캡이 캡 구실을 못 한다.
    // 그래서 방이 3개인 사용자는 교감을 30번 할 수 있지만 골드는 10번까지고, 11번째부터는
    // 429가 아니라 정상 응답에 earnedGold: 0 이 나간다(XP는 그대로 오른다).
    private static final long INTERACTION_GOLD = 200;
    private static final int DAILY_GOLD_LIMIT = 10;

    private final RoomMapper roomMapper;
    private final RoomService roomService;
    private final ExpService expService;
    private final ShopService shopService;

    public MascotService(RoomMapper roomMapper, RoomService roomService, ExpService expService, ShopService shopService) {
        this.roomMapper = roomMapper;
        this.roomService = roomService;
        this.expService = expService;
        this.shopService = shopService;
    }

    public RoomLevelResponse getLevel(long roomId, long userId) {
        roomService.assertActiveMember(roomId, userId);
        Room room = roomMapper.findById(roomId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        int level = room.getFriendshipLevel() == null ? 1 : room.getFriendshipLevel();
        // 만렙은 진행도를 남기지 않는다(계약 §12).
        int exp = level >= ExpService.MAX_LEVEL ? 0 : (room.getExpPoint() == null ? 0 : room.getExpPoint());
        int remaining = level >= ExpService.MAX_LEVEL ? 0 : ExpService.EXP_PER_LEVEL - exp;
        return new RoomLevelResponse(level, exp, ExpService.EXP_PER_LEVEL, remaining);
    }

    @Transactional
    public MascotInteractionResponse interact(long roomId, long userId) {
        roomService.assertActiveMember(roomId, userId);
        // "하루"는 사용자 기준(KST) 자정에 리셋된다 — created_at은 UTC로 저장되므로 경계도 UTC로 환산해 비교한다.
        LocalDateTime startOfDay = ClovTime.startOfTodayUtc();
        int usedToday = roomMapper.countMascotInteractionsToday(roomId, userId, startOfDay);
        if (usedToday >= DAILY_INTERACTION_LIMIT) {
            throw new DomainException(ErrorCode.MASCOT_INTERACTION_LIMIT_REACHED);
        }

        expService.grant(roomId, userId, ExpService.ACTION_MASCOT_INTERACT, INTERACTION_EXP, null);

        int goldUsedToday = shopService.countEarnedToday(userId, WalletTransaction.REASON_EARN_MASCOT);
        long earnedGold = 0;
        if (goldUsedToday < DAILY_GOLD_LIMIT) {
            earnedGold = shopService.earnGold(userId, WalletTransaction.REASON_EARN_MASCOT, INTERACTION_GOLD, null);
        }

        Room room = roomMapper.findById(roomId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return new MascotInteractionResponse(INTERACTION_EXP, DAILY_INTERACTION_LIMIT - usedToday - 1,
                room.getFriendshipLevel(), room.getExpPoint(), earnedGold);
    }
}
