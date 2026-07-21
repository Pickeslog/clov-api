package com.korit.clovapi.domain.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class PlanRequests {

    private PlanRequests() {
    }

    public record Create(@NotBlank @Size(max = 100) String title, LocalDate planDate, String description) {
    }

    public record Update(@Size(max = 100) String title, LocalDate planDate, String description) {
    }

    public record Checklist(@NotBlank @Size(max = 255) String content) {
    }

    public record ChecklistUpdate(@Size(max = 255) String content, Boolean checked) {
    }

    public record Stage(String stage, @NotBlank String imageUrl) {
    }

    public record Presign(@NotBlank String stage, @NotBlank String contentType) {
    }
}
