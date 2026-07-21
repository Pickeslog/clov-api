package com.korit.clovapi.domain.room.service;

import com.korit.clovapi.domain.room.dto.CreateRoomRequest;
import com.korit.clovapi.domain.room.dto.FavoriteRequest;
import com.korit.clovapi.domain.room.dto.FavoriteResponse;
import com.korit.clovapi.domain.room.dto.RoomDetailResponse;
import com.korit.clovapi.domain.room.dto.RoomMemberResponse;
import com.korit.clovapi.domain.room.dto.RoomMembersResponse;
import com.korit.clovapi.domain.room.dto.RoomSummariesResponse;
import com.korit.clovapi.domain.room.dto.RoomSummaryResponse;
import com.korit.clovapi.domain.room.dto.StatusMessageRequest;
import com.korit.clovapi.domain.room.dto.StatusMessageResponse;
import com.korit.clovapi.domain.room.dto.UpdateRoomRequest;
import com.korit.clovapi.domain.room.entity.Room;
import com.korit.clovapi.domain.room.entity.RoomMember;
import com.korit.clovapi.domain.room.mapper.RoomMapper;
import com.korit.clovapi.domain.room.mapper.RoomMemberMapper;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class RoomService {

    private final RoomMapper roomMapper;
    private final RoomMemberMapper roomMemberMapper;

    public RoomService(RoomMapper roomMapper, RoomMemberMapper roomMemberMapper) {
        this.roomMapper = roomMapper;
        this.roomMemberMapper = roomMemberMapper;
    }

    @Transactional
    public RoomDetailResponse create(long userId, CreateRoomRequest request) {
        Room room = new Room();
        room.setName(request.name());
        room.setDescription(request.description());
        room.setThemeColor(request.themeColor());
        room.setTransportType(request.transportType());
        room.setCoverPhotoUrl(request.coverPhotoUrl());
        room.setCoverTitle(request.coverTitle());
        roomMapper.insert(room);

        RoomMember member = new RoomMember();
        member.setRoomId(room.getId());
        member.setUserId(userId);
        roomMemberMapper.insert(member);
        return findDetail(room.getId(), userId);
    }

    public RoomDetailResponse findDetail(long roomId, long userId) {
        assertActiveMember(roomId, userId);
        return RoomDetailResponse.from(roomMapper.findDetailByIdAndUserId(roomId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
    }

    public RoomSummariesResponse findMyRooms(long userId) {
        return new RoomSummariesResponse(roomMapper.findSummariesByMemberUserId(userId).stream()
                .map(RoomSummaryResponse::from)
                .toList());
    }

    @Transactional
    public RoomDetailResponse update(long roomId, long userId, UpdateRoomRequest request) {
        assertActiveMember(roomId, userId);
        roomMapper.update(roomId, request);
        roomMapper.insertRoomUpdateNotifications(roomId, userId);
        return findDetail(roomId, userId);
    }

    public RoomMembersResponse findMembers(long roomId, long userId) {
        assertActiveMember(roomId, userId);
        return new RoomMembersResponse(roomMemberMapper.findByRoomId(roomId).stream()
                .map(RoomMemberResponse::from)
                .toList());
    }

    @Transactional
    public void leave(long roomId, long userId) {
        assertActiveMember(roomId, userId);
        roomMemberMapper.leave(roomId, userId, LocalDateTime.now(ZoneOffset.UTC));
        if (roomMemberMapper.countActiveByRoomId(roomId) == 0) {
            roomMapper.updateStatusInactive(roomId, LocalDateTime.now(ZoneOffset.UTC).plusDays(30));
        }
    }

    @Transactional
    public StatusMessageResponse updateStatusMessage(long roomId, long userId, StatusMessageRequest request) {
        assertActiveMember(roomId, userId);
        roomMemberMapper.updateStatusMessage(roomId, userId, request.statusMessage());
        return new StatusMessageResponse(request.statusMessage());
    }

    @Transactional
    public FavoriteResponse updateFavorite(long roomId, long userId, FavoriteRequest request) {
        assertActiveMember(roomId, userId);
        roomMemberMapper.updateFavorite(roomId, userId, request.isFavorite());
        return new FavoriteResponse(request.isFavorite());
    }

    @Transactional
    public RoomDetailResponse revive(long roomId, long userId) {
        Room room = roomMapper.findById(roomId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!"INACTIVE".equals(room.getStatus())) {
            throw new DomainException(ErrorCode.ROOM_MEMBER_NOT_FOUND);
        }
        if (roomMemberMapper.revive(roomId, userId, LocalDateTime.now(ZoneOffset.UTC)) != 1) {
            throw new DomainException(ErrorCode.ROOM_MEMBER_NOT_FOUND);
        }
        roomMapper.revive(roomId);
        return findDetail(roomId, userId);
    }

    public void assertActiveMember(long roomId, long userId) {
        if (roomMemberMapper.findActiveByRoomIdAndUserId(roomId, userId).isEmpty()) {
            throw new DomainException(ErrorCode.ROOM_MEMBER_NOT_FOUND);
        }
    }
}
