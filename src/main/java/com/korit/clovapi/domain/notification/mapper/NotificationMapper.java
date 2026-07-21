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
}
