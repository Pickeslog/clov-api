package com.korit.clovapi.domain.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class PlanRequests {

    private PlanRequests() {
    }

    /**
     * planType — 선택 입력. 생략하면 NORMAL(계약 §8-1). 허용값은 PlanService.PLAN_TYPES.
     * 값 검증을 애너테이션이 아니라 서비스에서 하는 이유: 허용 목록이 앞으로 늘 수 있어
     * (기념일 등) 한 곳에서만 관리해야 하고, stage 검증이 이미 같은 방식이다.
     */
    public record Create(@NotBlank @Size(max = 100) String title, LocalDate planDate, String description,
                         String planType) {
    }

    /**
     * ⚠️ planType 이 없다. 약속의 종류는 생성 시점에만 정해진다(계약 §8-1) — 바꾸려면
     * 지우고 다시 만든다. 여기 추가하면 계약을 어기는 것이다.
     */
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
