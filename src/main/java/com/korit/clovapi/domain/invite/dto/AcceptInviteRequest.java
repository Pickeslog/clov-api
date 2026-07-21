package com.korit.clovapi.domain.invite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInviteRequest(@NotBlank @Size(max = 20) String inviteCode) {
}
