package com.korit.clovapi.domain.plan.dto;

import com.korit.clovapi.domain.plan.entity.Plan;
import com.korit.clovapi.domain.plan.entity.PlanChecklist;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PlanResponses {
    private PlanResponses() {
    }

    public record Writer(String id, String nickname, String profileImageUrl) {
    }

    public record Checklist(String id, String content, Boolean checked) {
        public static Checklist from(PlanChecklist checklist) {
            return new Checklist(String.valueOf(checklist.getId()), checklist.getContent(), checklist.getChecked());
        }
    }

    /**
     * ⚠️ planType 은 항상 담는다 — null 일 때 생략하는 처리를 하지 말 것(계약 §8-1).
     * 목록에서 카드마다 색을 정해야 하는데 키가 없으면 그 자리만 판단이 안 선다.
     * (편지 title 은 @JsonInclude(NON_NULL) 로 생략하는데 그건 nullable 컬럼이라 반대다.
     *  plan_type 은 NOT NULL DEFAULT 'NORMAL' 이라 값이 없을 수가 없다.)
     */
    public record Summary(String id, String title, LocalDate planDate, String status, String memoryStatus,
                          String planType, Writer writer) {
        public static Summary from(Plan plan) {
            return new Summary(String.valueOf(plan.getId()), plan.getTitle(), plan.getPlanDate(), plan.getStatus(),
                    plan.getMemoryStatus(), plan.getPlanType(), PlanResponses.writer(plan));
        }
    }

    public record Detail(String id, String roomId, Writer writer, String title, LocalDate planDate, String description,
                         String status, String memoryStatus, String planType, LocalDateTime completedAt,
                         List<Checklist> checklists, LocalDateTime createdAt) {
        public static Detail from(Plan plan, List<PlanChecklist> checklists) {
            return new Detail(String.valueOf(plan.getId()), String.valueOf(plan.getRoomId()), PlanResponses.writer(plan),
                    plan.getTitle(), plan.getPlanDate(), plan.getDescription(), plan.getStatus(), plan.getMemoryStatus(),
                    plan.getPlanType(), plan.getCompletedAt(), checklists.stream().map(Checklist::from).toList(),
                    plan.getCreatedAt());
        }
    }

    public record Items<T>(List<T> items) {
    }

    public record Stage(String stage, String state, String imageUrl, Writer uploadedBy, LocalDateTime createdAt) {
    }

    public record Presign(String uploadUrl, String imageUrl, Integer expiresIn) {
    }

    private static Writer writer(Plan plan) {
        return new Writer(String.valueOf(plan.getWriterId()), plan.getWriterNickname(), plan.getWriterProfileImageUrl());
    }
}
