package com.korit.clovapi.domain.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 60) String description,
        @Size(max = 20) String themeColor,
        @Size(max = 20) String transportType,
        @Size(max = 512) String coverPhotoUrl,
        @Size(max = 100) String coverTitle
) {
}
