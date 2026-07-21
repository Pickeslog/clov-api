package com.korit.clovapi.domain.room.controller;

import com.korit.clovapi.domain.room.dto.MascotInteractionResponse;
import com.korit.clovapi.domain.room.service.MascotService;
import com.korit.clovapi.global.response.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
public class MascotController {

    private final MascotService mascotService;

    public MascotController(MascotService mascotService) {
        this.mascotService = mascotService;
    }

    @PostMapping("/{roomId}/mascot/interact")
    public ApiResponse<MascotInteractionResponse> interact(Authentication authentication, @PathVariable long roomId) {
        return ApiResponse.success(mascotService.interact(roomId, (Long) authentication.getPrincipal()));
    }
}
