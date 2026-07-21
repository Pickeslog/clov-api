package com.korit.clovapi.domain.room.dto;

public record MascotInteractionResponse(
        Integer expDelta, Integer remainingToday, Integer friendshipLevel, Integer expPoint
) {
}
