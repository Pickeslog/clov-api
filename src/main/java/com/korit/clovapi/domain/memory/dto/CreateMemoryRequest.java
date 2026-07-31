package com.korit.clovapi.domain.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateMemoryRequest(
        // 40 = 목업 정본(space.js:169 maxlength="40") · 계약 §10. 이전 25는 설계값이 아니라
        // 여기 박혀 있던 값이 계약으로 옮겨진 것이었다 — 프론트가 목업대로 40을 보내면
        // 25에서 400이 났다(clov-web #148/#185). 내리려면 프론트 maxLength도 같이 내릴 것.
        @NotBlank @Size(max = 40) String title,
        @Size(max = 100) String content,
        LocalDate memoryDate,
        List<@Size(max = 50) String> tags,
        List<String> participantUserIds
) {
}
