package com.korit.clovapi.domain.invite.controller;

import com.korit.clovapi.domain.invite.dto.AcceptInviteRequest;
import com.korit.clovapi.domain.invite.dto.CreateInviteRequest;
import com.korit.clovapi.domain.invite.dto.InviteResponse;
import com.korit.clovapi.domain.invite.dto.InvitesResponse;
import com.korit.clovapi.domain.invite.dto.JoinRequestResponse;
import com.korit.clovapi.domain.invite.service.InviteService;
import com.korit.clovapi.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping("/rooms/{roomId}/invites")
    public ResponseEntity<ApiResponse<InviteResponse>> create(
            Authentication authentication,
            @PathVariable long roomId,
            @Valid @RequestBody(required = false) CreateInviteRequest request
    ) {
        CreateInviteRequest body = request == null ? new CreateInviteRequest(null) : request;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(inviteService.create(roomId, currentUserId(authentication), body)));
    }

    @GetMapping("/rooms/{roomId}/invites")
    public ApiResponse<InvitesResponse> findByRoomId(Authentication authentication, @PathVariable long roomId) {
        return ApiResponse.success(inviteService.findByRoomId(roomId, currentUserId(authentication)));
    }

    @DeleteMapping("/invites/{inviteId}")
    public ApiResponse<Void> cancel(Authentication authentication, @PathVariable long inviteId) {
        inviteService.cancel(inviteId, currentUserId(authentication));
        return ApiResponse.success(null);
    }

    @PostMapping("/invites/accept")
    public ApiResponse<JoinRequestResponse> accept(
            Authentication authentication,
            @Valid @RequestBody AcceptInviteRequest request
    ) {
        return ApiResponse.success(inviteService.accept(currentUserId(authentication), request));
    }

    private long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
