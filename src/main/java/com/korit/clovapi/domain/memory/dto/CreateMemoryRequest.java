package com.korit.clovapi.domain.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateMemoryRequest(
        @NotBlank @Size(max = 25) String title,
        @Size(max = 100) String content,
        LocalDate memoryDate,
        List<@Size(max = 50) String> tags,
        List<String> participantUserIds
) {
}
