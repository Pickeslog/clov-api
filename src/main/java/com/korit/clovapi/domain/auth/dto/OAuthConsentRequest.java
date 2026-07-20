package com.korit.clovapi.domain.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OAuthConsentRequest(
        @NotBlank String registrationToken,
        @NotNull @Valid AgreementsRequest agreements
) {
}
