package com.korit.clovapi.domain.room.service;

import com.korit.clovapi.domain.room.dto.MascotInteractionResponse;
import com.korit.clovapi.domain.room.dto.RoomLevelResponse;
import com.korit.clovapi.domain.room.entity.Room;
import com.korit.clovapi.domain.room.mapper.RoomMapper;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import com.korit.clovapi.global.time.ClovTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MascotService {

    private static final int DAILY_INTERACTION_LIMIT = 3;
    private static final int INTERACTION_EXP = 2;

    private final RoomMapper roomMapper;
    private final RoomService roomService;
    private final ExpService expService;

    public MascotService(RoomMapper roomMapper, RoomService roomService, ExpService expService) {
        this.roomMapper = roomMapper;
        this.roomService = roomService;
        this.expService = expService;
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

        Room room = roomMapper.findById(roomId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return new MascotInteractionResponse(INTERACTION_EXP, DAILY_INTERACTION_LIMIT - usedToday - 1,
                room.getFriendshipLevel(), room.getExpPoint());
    }
}
