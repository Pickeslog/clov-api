package com.korit.clovapi.domain.notification.mapper;

import com.korit.clovapi.domain.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Optional;

@Mapper
public interface NotificationMapper {
    List<Notification> getNotifications(
            @Param("roomId") Long roomId,
            @Param("recipientId") Long recipientId,
            @Param("type") String type,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    Optional<Notification> findById(@Param("id") Long id);

    void markAsRead(@Param("id") Long id);

    int markAllAsRead(@Param("roomId") Long roomId, @Param("recipientId") Long recipientId);

    // 방 ACTIVE 멤버 전원에게 팬아웃(actorId 있으면 본인 제외). actorId·payload는 nullable.
    void fanOutToActiveMembers(@Param("roomId") long roomId, @Param("actorId") Long actorId,
                               @Param("type") String type, @Param("subType") String subType,
                               @Param("referenceId") Long referenceId, @Param("payload") String payload);

    // 특정 수신자 1명에게(예: 편지). actorId·payload는 nullable.
    void insertOne(@Param("roomId") long roomId, @Param("recipientId") long recipientId,
                   @Param("actorId") Long actorId, @Param("type") String type, @Param("subType") String subType,
                   @Param("referenceId") Long referenceId, @Param("payload") String payload);

    // 종 아이콘 배지용(계약 §13, web-design-repository#89). room 무관, 유저 전체 기준 —
    // idx_notifications_recipient(recipient_id, is_read, created_at)로 EXISTS만 확인한다.
    boolean existsUnread(@Param("recipientId") long recipientId);

    // 방 안 종 아이콘 배지용(계약 §13, clov-api#174). 위 existsUnread(전체)와 달리 이 방만 기준.
    boolean existsUnreadInRoom(@Param("recipientId") long recipientId, @Param("roomId") long roomId);
}
