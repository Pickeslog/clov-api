package com.korit.clovapi.domain.room.dto;

public record RoomLevelResponse(
        Integer friendshipLevel, Integer expPoint, Integer expForNextLevel, Integer remainingToNextLevel
) {
}
