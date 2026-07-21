package com.korit.clovapi.domain.invite.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateInviteRequest(@Min(1) @Max(720) Integer expiresInHours) {
}
