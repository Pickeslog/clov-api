package com.korit.clovapi.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthLinkConfirmRequest(@NotBlank String registrationToken) {
}
