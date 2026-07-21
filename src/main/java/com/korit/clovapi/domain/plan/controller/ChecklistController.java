package com.korit.clovapi.domain.plan.controller;

import com.korit.clovapi.domain.plan.dto.PlanRequests;
import com.korit.clovapi.domain.plan.dto.PlanResponses;
import com.korit.clovapi.domain.plan.service.PlanService;
import com.korit.clovapi.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ChecklistController {

    private final PlanService planService;

    public ChecklistController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping("/plans/{planId}/checklists")
    public ApiResponse<PlanResponses.Checklist> add(Authentication authentication, @PathVariable long planId,
                                                      @Valid @RequestBody PlanRequests.Checklist request) {
        return ApiResponse.success(planService.addChecklist(planId, currentUserId(authentication), request));
    }

    @PatchMapping("/checklists/{checklistId}")
    public ApiResponse<PlanResponses.Checklist> update(Authentication authentication, @PathVariable long checklistId,
                                                         @Valid @RequestBody PlanRequests.ChecklistUpdate request) {
        return ApiResponse.success(planService.updateChecklist(checklistId, currentUserId(authentication), request));
    }

    @DeleteMapping("/checklists/{checklistId}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable long checklistId) {
        planService.deleteChecklist(checklistId, currentUserId(authentication));
        return ApiResponse.success(null);
    }

    private long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
