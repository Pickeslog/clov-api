package com.korit.clovapi.domain.notification.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.korit.clovapi.domain.notification.entity.Notification;
import com.korit.clovapi.global.dto.UserSummaryResponse;
import java.time.LocalDateTime;

public record NotificationResponse(
        String id,
        String roomId,
        String recipientId,
        UserSummaryResponse actor,
        String type,
        String subType,
        String referenceId,
        Object payload,
        Boolean isRead,
        LocalDateTime createdAt
) {
    // payload는 DB에 JSON 문자열로 저장되지만 응답에서는 객체로 내보낸다(프론트가 payload.level로 읽음).
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
                notification.getSubType(),
                notification.getReferenceId() != null ? String.valueOf(notification.getReferenceId()) : null,
                parsePayload(notification.getPayload()),
                notification.getIsRead(),
                notification.getCreatedAt()
        );
    }

    private static Object parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(payload, Object.class);
        } catch (Exception e) {
            // 손상된 payload 하나 때문에 알림 목록 전체가 죽으면 안 된다 → null로 흘린다.
            return null;
        }
    }
}
