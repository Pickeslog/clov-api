package com.korit.clovapi.domain.plan.controller;

import com.korit.clovapi.domain.plan.dto.PlanRequests;
import com.korit.clovapi.domain.plan.dto.PlanResponses;
import com.korit.clovapi.domain.plan.service.PlanService;
import com.korit.clovapi.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping("/rooms/{roomId}/plans")
    public ResponseEntity<ApiResponse<PlanResponses.Detail>> create(Authentication authentication, @PathVariable long roomId,
                                                                       @Valid @RequestBody PlanRequests.Create request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(planService.create(roomId, currentUserId(authentication), request)));
    }

    @GetMapping("/rooms/{roomId}/plans")
    public ApiResponse<PlanResponses.Items<PlanResponses.Summary>> list(Authentication authentication,
                                                                           @PathVariable long roomId,
                                                                           @RequestParam(required = false) String status,
                                                                           @RequestParam(required = false) LocalDate from,
                                                                           @RequestParam(required = false) LocalDate to) {
        return ApiResponse.success(planService.list(roomId, currentUserId(authentication), status, from, to));
    }

    @GetMapping("/plans/{planId}")
    public ApiResponse<PlanResponses.Detail> detail(Authentication authentication, @PathVariable long planId) {
        return ApiResponse.success(planService.detail(planId, currentUserId(authentication)));
    }

    @PatchMapping("/plans/{planId}")
    public ApiResponse<PlanResponses.Detail> update(Authentication authentication, @PathVariable long planId,
                                                      @Valid @RequestBody PlanRequests.Update request) {
        return ApiResponse.success(planService.update(planId, currentUserId(authentication), request));
    }

    @DeleteMapping("/plans/{planId}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable long planId) {
        planService.delete(planId, currentUserId(authentication));
        return ApiResponse.success(null);
    }

    @PostMapping("/plans/{planId}/complete")
    public ApiResponse<PlanResponses.Detail> complete(Authentication authentication, @PathVariable long planId) {
        return ApiResponse.success(planService.complete(planId, currentUserId(authentication)));
    }

    @PostMapping("/plans/{planId}/cancel")
    public ApiResponse<PlanResponses.Detail> cancel(Authentication authentication, @PathVariable long planId) {
        return ApiResponse.success(planService.cancel(planId, currentUserId(authentication)));
    }

    @PostMapping("/plans/{planId}/skip-memory")
    public ApiResponse<PlanResponses.Detail> skip(Authentication authentication, @PathVariable long planId) {
        return ApiResponse.success(planService.skip(planId, currentUserId(authentication)));
    }

    @GetMapping("/plans/{planId}/stage-photos")
    public ApiResponse<PlanResponses.Items<PlanResponses.Stage>> stages(Authentication authentication,
                                                                           @PathVariable long planId) {
        return ApiResponse.success(planService.stages(planId, currentUserId(authentication)));
    }

    @PostMapping("/plans/{planId}/stage-photos/presign")
    public ApiResponse<PlanResponses.Presign> presign(Authentication authentication, @PathVariable long planId,
                                                        @Valid @RequestBody PlanRequests.Presign request) {
        return ApiResponse.success(planService.presign(planId, currentUserId(authentication), request));
    }

    @PostMapping("/plans/{planId}/stage-photos")
    public ApiResponse<PlanResponses.Stage> commit(Authentication authentication, @PathVariable long planId,
                                                    @Valid @RequestBody PlanRequests.Stage request) {
        return ApiResponse.success(planService.commit(planId, currentUserId(authentication), request));
    }

    private long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
