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

    public record Summary(String id, String title, LocalDate planDate, String status, String memoryStatus, Writer writer) {
        public static Summary from(Plan plan) {
            return new Summary(String.valueOf(plan.getId()), plan.getTitle(), plan.getPlanDate(), plan.getStatus(),
                    plan.getMemoryStatus(), PlanResponses.writer(plan));
        }
    }

    public record Detail(String id, String roomId, Writer writer, String title, LocalDate planDate, String description,
                         String status, String memoryStatus, LocalDateTime completedAt, List<Checklist> checklists,
                         LocalDateTime createdAt) {
        public static Detail from(Plan plan, List<PlanChecklist> checklists) {
            return new Detail(String.valueOf(plan.getId()), String.valueOf(plan.getRoomId()), PlanResponses.writer(plan),
                    plan.getTitle(), plan.getPlanDate(), plan.getDescription(), plan.getStatus(), plan.getMemoryStatus(),
                    plan.getCompletedAt(), checklists.stream().map(Checklist::from).toList(), plan.getCreatedAt());
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
