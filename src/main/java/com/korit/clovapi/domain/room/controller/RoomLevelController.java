package com.korit.clovapi.domain.room.controller;

import com.korit.clovapi.domain.room.dto.ExpLogResponse;
import com.korit.clovapi.domain.room.dto.ExpLogsResponse;
import com.korit.clovapi.domain.room.dto.RoomLevelResponse;
import com.korit.clovapi.domain.room.mapper.RoomMapper;
import com.korit.clovapi.domain.room.service.MascotService;
import com.korit.clovapi.domain.room.service.RoomService;
import com.korit.clovapi.global.response.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomLevelController {

    private final RoomService roomService;
    private final MascotService mascotService;
    private final RoomMapper roomMapper;

    public RoomLevelController(RoomService roomService, MascotService mascotService, RoomMapper roomMapper) {
        this.roomService = roomService;
        this.mascotService = mascotService;
        this.roomMapper = roomMapper;
    }

    @GetMapping("/{roomId}/exp-logs")
    public ApiResponse<ExpLogsResponse> findExpLogs(Authentication authentication, @PathVariable long roomId) {
        long userId = currentUserId(authentication);
        roomService.assertActiveMember(roomId, userId);
        return ApiResponse.success(new ExpLogsResponse(roomMapper.findExpLogs(roomId).stream()
                .map(ExpLogResponse::from)
                .toList()));
    }

    @GetMapping("/{roomId}/level")
    public ApiResponse<RoomLevelResponse> getLevel(Authentication authentication, @PathVariable long roomId) {
        return ApiResponse.success(mascotService.getLevel(roomId, currentUserId(authentication)));
    }

    private long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
