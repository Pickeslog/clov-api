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
import com.korit.clovapi.global.dto.PresignRequest;
import com.korit.clovapi.global.dto.PresignResponse;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import com.korit.clovapi.global.storage.StoragePresigner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class RoomService {

    private final RoomMapper roomMapper;
    private final RoomMemberMapper roomMemberMapper;
    private final StoragePresigner storagePresigner;

    public RoomService(RoomMapper roomMapper, RoomMemberMapper roomMemberMapper, StoragePresigner storagePresigner) {
        this.roomMapper = roomMapper;
        this.roomMemberMapper = roomMemberMapper;
        this.storagePresigner = storagePresigner;
    }

    /**
     * 대표 커버 이미지 업로드용 presigned PUT URL 발급(공간 멤버 누구나, 계약 §6·§4-3).
     * 클라가 uploadUrl로 PUT 후 PATCH /rooms/{id}의 coverPhotoUrl에 imageUrl을 넣어 커밋한다.
     * 파일 저장/행 생성은 없고 서명만 한다(로컬 계산).
     */
    public PresignResponse presignCoverImage(long roomId, long userId, PresignRequest request) {
        assertActiveMember(roomId, userId);
        String objectKey = "rooms/%d/cover/%s%s".formatted(
                roomId, UUID.randomUUID(), StoragePresigner.extensionFor(request.contentType()));
        return PresignResponse.from(storagePresigner.presignPut(objectKey, request.contentType()));
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
