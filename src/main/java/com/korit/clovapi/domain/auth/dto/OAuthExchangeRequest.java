package com.korit.clovapi.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthExchangeRequest(@NotBlank String code) {
}
