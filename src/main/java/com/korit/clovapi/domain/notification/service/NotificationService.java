package com.korit.clovapi.domain.notification.service;

import com.korit.clovapi.domain.notification.dto.NotificationResponse;
import com.korit.clovapi.domain.notification.dto.NotificationsResponse;
import com.korit.clovapi.domain.notification.dto.ReadAllResponse;
import com.korit.clovapi.domain.notification.entity.Notification;
import com.korit.clovapi.domain.notification.mapper.NotificationMapper;
import com.korit.clovapi.domain.room.mapper.RoomMemberMapper;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    // 탭(계약 §13) — 목록 조회 ?type= 필터가 이 값을 쓴다.
    public static final String TYPE_FRIEND = "FRIEND";
    public static final String TYPE_JOIN = "JOIN";
    public static final String TYPE_NOTICE = "NOTICE";
    // 이벤트(계약 §13)
    public static final String SUB_ROOM_UPDATE = "ROOM_UPDATE";
    public static final String SUB_MEMORY_WRITE = "MEMORY_WRITE";
    public static final String SUB_PLAN_CREATE = "PLAN_CREATE";
    public static final String SUB_PLAN_COMPLETE = "PLAN_COMPLETE";
    public static final String SUB_LEVEL_UP = "LEVEL_UP";
    public static final String SUB_JOIN_REQUEST = "JOIN_REQUEST";
    public static final String SUB_MEMBER_JOINED = "MEMBER_JOINED";
    public static final String SUB_JOIN_ACCEPTED = "JOIN_ACCEPTED";
    public static final String SUB_MEMBER_LEFT = "MEMBER_LEFT";
    public static final String SUB_COMMENT = "COMMENT";
    public static final String SUB_LETTER_RECEIVE = "LETTER_RECEIVE";

    private final NotificationMapper notificationMapper;
    private final RoomMemberMapper roomMemberMapper;

    public NotificationService(NotificationMapper notificationMapper, RoomMemberMapper roomMemberMapper) {
        this.notificationMapper = notificationMapper;
        this.roomMemberMapper = roomMemberMapper;
    }

    // RoomService.assertActiveMember와 같은 검사다. RoomService를 그대로 주입하면
    // RoomService → NotificationService(#122, fanOut) → RoomService 순환 의존이 생겨서,
    // InviteService처럼 각 도메인이 RoomMemberMapper로 자체 검사한다.
    private void assertActiveMember(long roomId, long userId) {
        if (roomMemberMapper.findActiveByRoomIdAndUserId(roomId, userId).isEmpty()) {
            throw new DomainException(ErrorCode.ROOM_MEMBER_NOT_FOUND);
        }
    }

    public NotificationsResponse getNotifications(Long roomId, Long requesterId, String type, int page, int size) {
        assertActiveMember(roomId, requesterId);

        int offset = page * size;
        List<NotificationResponse> items = notificationMapper.getNotifications(roomId, requesterId, type, offset, size)
                .stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
        return new NotificationsResponse(items);
    }

    @Transactional
    public void markAsRead(Long id, Long requesterId) {
        Notification notification = notificationMapper.findById(id)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        
        if (!notification.getRecipientId().equals(requesterId)) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        
        notificationMapper.markAsRead(id);
    }

    @Transactional
    public ReadAllResponse markAllAsRead(Long roomId, Long requesterId) {
        assertActiveMember(roomId, requesterId);

        int updatedCount = notificationMapper.markAllAsRead(roomId, requesterId);
        return new ReadAllResponse(updatedCount);
    }

    /**
     * 방 ACTIVE 멤버 전원에게 알림 팬아웃(계약 §13). actorId가 있으면 본인은 제외한다.
     * 각 도메인 서비스(추억·약속·레벨업 등)가 자기 트랜잭션 안에서 호출한다.
     */
    public void fanOut(long roomId, Long actorId, String type, String subType, Long referenceId, String payload) {
        notificationMapper.fanOutToActiveMembers(roomId, actorId, type, subType, referenceId, payload);
    }

    /** 특정 수신자 1명에게 알림(예: 편지 수신). */
    public void notifyOne(long roomId, long recipientId, Long actorId, String type, String subType,
                          Long referenceId, String payload) {
        notificationMapper.insertOne(roomId, recipientId, actorId, type, subType, referenceId, payload);
    }
}
