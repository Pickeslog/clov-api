package com.korit.clovapi.domain.room.dto;

import com.korit.clovapi.domain.room.entity.Room;

import java.time.LocalDateTime;

public record RoomSummaryResponse(
        String id,
        String name,
        String description,
        String themeColor,
        String transportType,
        String coverPhotoUrl,
        Integer friendshipLevel,
        Integer memberCount,
        Boolean isFavorite,
        String status,
        LocalDateTime createdAt
) {
    public static RoomSummaryResponse from(Room room) {
        return new RoomSummaryResponse(
                String.valueOf(room.getId()),
                room.getName(),
                room.getDescription(),
                room.getThemeColor(),
                room.getTransportType(),
                room.getCoverPhotoUrl(),
                room.getFriendshipLevel(),
                room.getMemberCount(),
                room.getFavorite(),
                room.getStatus(),
                room.getCreatedAt()
        );
    }
}
