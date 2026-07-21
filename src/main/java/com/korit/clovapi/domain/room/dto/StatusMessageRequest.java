package com.korit.clovapi.domain.room.dto;

import jakarta.validation.constraints.Size;

public record StatusMessageRequest(@Size(max = 100) String statusMessage) {
}
