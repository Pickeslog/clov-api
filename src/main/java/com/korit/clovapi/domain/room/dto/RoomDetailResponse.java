package com.korit.clovapi.domain.room.dto;

import com.korit.clovapi.domain.room.entity.Room;

import java.time.LocalDateTime;

public record RoomDetailResponse(
        String id, String name, String description, String themeColor, String transportType,
        String coverPhotoUrl, String coverTitle, Integer friendshipLevel, Integer expPoint,
        String status, Integer memberCount, Boolean isFavorite, String myStatusMessage,
        LocalDateTime scheduledDeleteAt, LocalDateTime createdAt
) {
    public static RoomDetailResponse from(Room room) {
        return new RoomDetailResponse(String.valueOf(room.getId()), room.getName(), room.getDescription(),
                room.getThemeColor(), room.getTransportType(), room.getCoverPhotoUrl(), room.getCoverTitle(),
                room.getFriendshipLevel(), room.getExpPoint(), room.getStatus(), room.getMemberCount(),
                room.getFavorite(), room.getMyStatusMessage(), room.getScheduledDeleteAt(), room.getCreatedAt());
    }
}
