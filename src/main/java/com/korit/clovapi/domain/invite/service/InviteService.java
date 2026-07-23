package com.korit.clovapi.domain.invite.service;

import com.korit.clovapi.domain.invite.dto.AcceptInviteRequest;
import com.korit.clovapi.domain.invite.dto.CreateInviteRequest;
import com.korit.clovapi.domain.invite.dto.InviteResponse;
import com.korit.clovapi.domain.invite.dto.InvitesResponse;
import com.korit.clovapi.domain.invite.dto.AcceptJoinRequestResponse;
import com.korit.clovapi.domain.invite.dto.JoinRequestListItemResponse;
import com.korit.clovapi.domain.invite.dto.JoinRequestsResponse;
import com.korit.clovapi.domain.invite.dto.JoinRequestResponse;
import com.korit.clovapi.domain.invite.dto.MyJoinRequestResponse;
import com.korit.clovapi.domain.invite.dto.MyJoinRequestsResponse;
import com.korit.clovapi.domain.invite.entity.RoomInvite;
import com.korit.clovapi.domain.invite.entity.RoomJoinRequest;
import com.korit.clovapi.domain.invite.mapper.InviteMapper;
import com.korit.clovapi.domain.invite.mapper.JoinRequestMapper;
import com.korit.clovapi.domain.room.mapper.RoomMemberMapper;
import com.korit.clovapi.domain.room.entity.RoomMember;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class InviteService {

    private static final char[] BASE32 = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_EXPIRES_IN_HOURS = 72;
    private static final int MAX_ROOM_MEMBERS = 8;

    private final InviteMapper inviteMapper;
    private final JoinRequestMapper joinRequestMapper;
    private final RoomMemberMapper roomMemberMapper;

    public InviteService(
            InviteMapper inviteMapper,
            JoinRequestMapper joinRequestMapper,
            RoomMemberMapper roomMemberMapper
    ) {
        this.inviteMapper = inviteMapper;
        this.joinRequestMapper = joinRequestMapper;
        this.roomMemberMapper = roomMemberMapper;
    }

    @Transactional
    public InviteResponse create(long roomId, long userId, CreateInviteRequest request) {
        assertActiveMember(roomId, userId);
        int expiresInHours = request.expiresInHours() == null ? DEFAULT_EXPIRES_IN_HOURS : request.expiresInHours();
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(expiresInHours);
        // A안(방당 고정 회전 코드): 방마다 한 행. "재발급"은 새 행 INSERT가 아니라 제자리 회전(upsert).
        // → USED/CANCELED 행이 누적되지 않는다.
        inviteMapper.upsertByRoomId(roomId, nextInviteCode(), userId, expiresAt);
        return InviteResponse.from(activeInvite(roomId));
    }

    public InvitesResponse findByRoomId(long roomId, long userId) {
        assertActiveMember(roomId, userId);
        return new InvitesResponse(inviteMapper.findActiveByRoomId(roomId).stream().map(InviteResponse::from).toList());
    }

    private RoomInvite activeInvite(long roomId) {
        return inviteMapper.findActiveByRoomId(roomId).stream().findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    @Transactional
    public void cancel(long inviteId, long userId) {
        RoomInvite invite = inviteMapper.findById(inviteId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (invite.getCreatedBy() != userId || inviteMapper.cancelByIdAndCreatorId(inviteId, userId) != 1) {
            throw new DomainException(ErrorCode.NOT_WRITER);
        }
    }

    @Transactional
    public JoinRequestResponse accept(long userId, AcceptInviteRequest request) {
        RoomInvite invite = inviteMapper.findByInviteCode(request.inviteCode())
                .orElseThrow(() -> new DomainException(ErrorCode.INVITE_EXPIRED));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        boolean expired = invite.getExpiresAt() != null && !invite.getExpiresAt().isAfter(now);
        // A안: 초대 코드는 방당 고정·다회용 — 수락해도 USED로 소모하지 않는다(여러 친구가 한 코드로 신청 가능).
        // 유효하지 않은 코드(취소됨=CANCELED 또는 만료)는 모두 INVITE_EXPIRED로 통일.
        if (!"ACTIVE".equals(invite.getStatus()) || expired) {
            throw new DomainException(ErrorCode.INVITE_EXPIRED);
        }
        if (roomMemberMapper.findActiveByRoomIdAndUserId(invite.getRoomId(), userId).isPresent()) {
            throw new DomainException(ErrorCode.ROOM_MEMBER_NOT_FOUND);
        }

        RoomJoinRequest joinRequest = new RoomJoinRequest();
        joinRequest.setRoomId(invite.getRoomId());
        joinRequest.setUserId(userId);
        joinRequest.setInviteId(invite.getId());
        joinRequestMapper.insert(joinRequest);
        return JoinRequestResponse.from(joinRequestMapper.findById(joinRequest.getId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
    }

    public JoinRequestsResponse findPending(long roomId, long userId) {
        assertActiveMember(roomId, userId);
        return new JoinRequestsResponse(joinRequestMapper.findPendingByRoomId(roomId).stream()
                .map(JoinRequestListItemResponse::from)
                .toList());
    }

    /** 내가 보낸 가입 신청(요청한 방) — PENDING·REJECTED. */
    public MyJoinRequestsResponse findMyJoinRequests(long userId) {
        return new MyJoinRequestsResponse(joinRequestMapper.findMineByUserId(userId).stream()
                .map(MyJoinRequestResponse::from)
                .toList());
    }

    /** 내 PENDING 신청 취소(요청 취소). 본인 것이 아니거나 이미 처리됐으면 NOT_FOUND. */
    @Transactional
    public void cancelMyJoinRequest(long joinRequestId, long userId) {
        if (joinRequestMapper.cancelPendingByIdAndUserId(joinRequestId, userId) != 1) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
    }

    @Transactional
    public AcceptJoinRequestResponse acceptJoinRequest(long joinRequestId, long userId) {
        RoomJoinRequest request = findPending(joinRequestId);
        assertActiveMember(request.getRoomId(), userId);
        if (roomMemberMapper.countActiveForUpdateByRoomId(request.getRoomId()) >= MAX_ROOM_MEMBERS) {
            throw new DomainException(ErrorCode.ROOM_CAPACITY_EXCEEDED);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime undoDeadlineAt = now.plusMinutes(5);
        if (joinRequestMapper.acceptWithVersion(joinRequestId, request.getVersion(), userId, now, undoDeadlineAt) != 1) {
            throw new DomainException(ErrorCode.JOIN_REQUEST_ALREADY_PROCESSED);
        }
        // 이미 ACTIVE면 그대로 두고(멱등), 과거 나간(LEFT) 이력이 있으면 부활(revive), 처음이면 insert.
        // room_members UNIQUE(room_id,user_id) 때문에 "나갔다 재입장"(LEFT 행 잔존) 시 bare insert가
        // 중복 위반으로 500나던 것을 방지한다.
        if (roomMemberMapper.findActiveByRoomIdAndUserId(request.getRoomId(), request.getUserId()).isEmpty()
                && roomMemberMapper.revive(request.getRoomId(), request.getUserId(), now) != 1) {
            RoomMember fresh = new RoomMember();
            fresh.setRoomId(request.getRoomId());
            fresh.setUserId(request.getUserId());
            roomMemberMapper.insert(fresh);
        }
        RoomMember member = roomMemberMapper.findActiveByRoomIdAndUserId(request.getRoomId(), request.getUserId())
                .orElseThrow(() -> new DomainException(ErrorCode.ROOM_MEMBER_NOT_FOUND));
        inviteMapper.insertJoinNotifications(request.getRoomId(), userId, joinRequestId);
        return new AcceptJoinRequestResponse(String.valueOf(member.getId()), String.valueOf(request.getRoomId()),
                String.valueOf(request.getUserId()), undoDeadlineAt);
    }

    @Transactional
    public void rejectJoinRequest(long joinRequestId, long userId) {
        RoomJoinRequest request = findPending(joinRequestId);
        assertActiveMember(request.getRoomId(), userId);
        if (joinRequestMapper.rejectWithVersion(joinRequestId, request.getVersion(), LocalDateTime.now(ZoneOffset.UTC)) != 1) {
            throw new DomainException(ErrorCode.JOIN_REQUEST_ALREADY_PROCESSED);
        }
    }

    @Transactional
    public void undoJoinRequest(long joinRequestId, long userId) {
        RoomJoinRequest request = joinRequestMapper.findById(joinRequestId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!"ACCEPTED".equals(request.getStatus())) {
            throw new DomainException(ErrorCode.JOIN_REQUEST_ALREADY_PROCESSED);
        }
        if (request.getAcceptedBy() != userId) {
            throw new DomainException(ErrorCode.NOT_WRITER);
        }
        if (!request.getUndoDeadlineAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new DomainException(ErrorCode.JOIN_REQUEST_UNDO_EXPIRED);
        }
        if (joinRequestMapper.undoWithVersion(joinRequestId, request.getVersion(), userId) != 1) {
            throw new DomainException(ErrorCode.JOIN_REQUEST_ALREADY_PROCESSED);
        }
        roomMemberMapper.deleteByRoomIdAndUserId(request.getRoomId(), request.getUserId());
    }

    private void assertActiveMember(long roomId, long userId) {
        if (roomMemberMapper.findActiveByRoomIdAndUserId(roomId, userId).isEmpty()) {
            throw new DomainException(ErrorCode.ROOM_MEMBER_NOT_FOUND);
        }
    }

    private RoomJoinRequest findPending(long joinRequestId) {
        RoomJoinRequest request = joinRequestMapper.findById(joinRequestId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!"PENDING".equals(request.getStatus())) {
            throw new DomainException(ErrorCode.JOIN_REQUEST_ALREADY_PROCESSED);
        }
        return request;
    }

    private String nextInviteCode() {
        String inviteCode;
        do {
            StringBuilder suffix = new StringBuilder(6);
            for (int index = 0; index < 6; index++) {
                suffix.append(BASE32[RANDOM.nextInt(BASE32.length)]);
            }
            inviteCode = "CLV-JOIN-" + suffix;
        } while (inviteMapper.existsByInviteCode(inviteCode));
        return inviteCode;
    }
}
