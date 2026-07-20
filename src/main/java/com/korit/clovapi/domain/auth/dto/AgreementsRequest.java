package com.korit.clovapi.domain.auth.dto;

import jakarta.validation.constraints.NotNull;

public record AgreementsRequest(
        @NotNull Boolean service,
        @NotNull Boolean privacy,
        @NotNull Boolean marketing
) {
}
