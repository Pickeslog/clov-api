package com.korit.clovapi.domain.invite.controller;

import com.korit.clovapi.domain.invite.dto.AcceptJoinRequestResponse;
import com.korit.clovapi.domain.invite.dto.JoinRequestsResponse;
import com.korit.clovapi.domain.invite.service.InviteService;
import com.korit.clovapi.global.response.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class JoinRequestController {

    private final InviteService inviteService;

    public JoinRequestController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping("/rooms/{roomId}/join-requests")
    public ApiResponse<JoinRequestsResponse> findPending(Authentication authentication, @PathVariable long roomId) {
        return ApiResponse.success(inviteService.findPending(roomId, currentUserId(authentication)));
    }

    @PostMapping("/join-requests/{joinRequestId}/accept")
    public ApiResponse<AcceptJoinRequestResponse> accept(Authentication authentication, @PathVariable long joinRequestId) {
        return ApiResponse.success(inviteService.acceptJoinRequest(joinRequestId, currentUserId(authentication)));
    }

    @PostMapping("/join-requests/{joinRequestId}/reject")
    public ApiResponse<Void> reject(Authentication authentication, @PathVariable long joinRequestId) {
        inviteService.rejectJoinRequest(joinRequestId, currentUserId(authentication));
        return ApiResponse.success(null);
    }

    @PostMapping("/join-requests/{joinRequestId}/undo")
    public ApiResponse<Void> undo(Authentication authentication, @PathVariable long joinRequestId) {
        inviteService.undoJoinRequest(joinRequestId, currentUserId(authentication));
        return ApiResponse.success(null);
    }

    private long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
