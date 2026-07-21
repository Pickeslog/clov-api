package com.korit.clovapi.domain.room.service;

import com.korit.clovapi.domain.room.dto.MascotInteractionResponse;
import com.korit.clovapi.domain.room.dto.RoomLevelResponse;
import com.korit.clovapi.domain.room.entity.FriendshipExpLog;
import com.korit.clovapi.domain.room.entity.Room;
import com.korit.clovapi.domain.room.mapper.RoomMapper;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class MascotService {

    private static final int DAILY_INTERACTION_LIMIT = 3;
    private static final int INTERACTION_EXP = 2;
    private static final int EXP_FOR_NEXT_LEVEL = 500;

    private final RoomMapper roomMapper;
    private final RoomService roomService;

    public MascotService(RoomMapper roomMapper, RoomService roomService) {
        this.roomMapper = roomMapper;
        this.roomService = roomService;
    }

    public RoomLevelResponse getLevel(long roomId, long userId) {
        roomService.assertActiveMember(roomId, userId);
        Room room = roomMapper.findById(roomId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        int remaining = Math.max(0, EXP_FOR_NEXT_LEVEL - room.getExpPoint());
        return new RoomLevelResponse(room.getFriendshipLevel(), room.getExpPoint(), EXP_FOR_NEXT_LEVEL, remaining);
    }

    @Transactional
    public MascotInteractionResponse interact(long roomId, long userId) {
        roomService.assertActiveMember(roomId, userId);
        LocalDateTime startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        int usedToday = roomMapper.countMascotInteractionsToday(roomId, userId, startOfDay);
        if (usedToday >= DAILY_INTERACTION_LIMIT) {
            throw new DomainException(ErrorCode.MASCOT_INTERACTION_LIMIT_REACHED);
        }

        FriendshipExpLog log = new FriendshipExpLog();
        log.setRoomId(roomId);
        log.setTriggeredBy(userId);
        log.setActionType("MASCOT_INTERACT");
        log.setExpDelta(INTERACTION_EXP);
        roomMapper.insertExpLog(log);
        roomMapper.addExp(roomId, INTERACTION_EXP);

        Room room = roomMapper.findById(roomId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return new MascotInteractionResponse(INTERACTION_EXP, DAILY_INTERACTION_LIMIT - usedToday - 1,
                room.getFriendshipLevel(), room.getExpPoint());
    }
}
