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
}
