package com.korit.clovapi.domain.room.dto;

import jakarta.validation.constraints.NotNull;

public record FavoriteRequest(@NotNull Boolean isFavorite) {
}
