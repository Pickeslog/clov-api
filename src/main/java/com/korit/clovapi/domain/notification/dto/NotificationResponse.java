package com.korit.clovapi.domain.notification.dto;

import com.korit.clovapi.domain.notification.entity.Notification;
import com.korit.clovapi.global.dto.UserSummaryResponse;
import java.time.LocalDateTime;

public record NotificationResponse(
        String id,
        String roomId,
        String recipientId,
        UserSummaryResponse actor,
        String type,
        String referenceId,
        Boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        UserSummaryResponse actor = notification.getActorId() != null ?
                new UserSummaryResponse(
                        String.valueOf(notification.getActorId()),
                        notification.getActorNickname(),
                        notification.getActorProfileImageUrl()
                ) : null;

        return new NotificationResponse(
                notification.getId() != null ? String.valueOf(notification.getId()) : null,
                notification.getRoomId() != null ? String.valueOf(notification.getRoomId()) : null,
                notification.getRecipientId() != null ? String.valueOf(notification.getRecipientId()) : null,
                actor,
                notification.getType(),
                notification.getReferenceId() != null ? String.valueOf(notification.getReferenceId()) : null,
                notification.getIsRead(),
                notification.getCreatedAt()
        );
    }
}
