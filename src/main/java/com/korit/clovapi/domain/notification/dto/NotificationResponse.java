package com.korit.clovapi.domain.notification.dto;

import com.korit.clovapi.domain.notification.entity.Notification;
import java.time.LocalDateTime;

public record NotificationResponse(
        String id,
        String roomId,
        String recipientId,
        String actorId,
        String type,
        String referenceId,
        Boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId() != null ? String.valueOf(notification.getId()) : null,
                notification.getRoomId() != null ? String.valueOf(notification.getRoomId()) : null,
                notification.getRecipientId() != null ? String.valueOf(notification.getRecipientId()) : null,
                notification.getActorId() != null ? String.valueOf(notification.getActorId()) : null,
                notification.getType(),
                notification.getReferenceId() != null ? String.valueOf(notification.getReferenceId()) : null,
                notification.getIsRead(),
                notification.getCreatedAt()
        );
    }
}
