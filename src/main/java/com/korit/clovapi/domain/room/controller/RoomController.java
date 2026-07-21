package com.korit.clovapi.domain.room.controller;

import com.korit.clovapi.domain.room.dto.CreateRoomRequest;
import com.korit.clovapi.domain.room.dto.FavoriteRequest;
import com.korit.clovapi.domain.room.dto.FavoriteResponse;
import com.korit.clovapi.domain.room.dto.RoomDetailResponse;
import com.korit.clovapi.domain.room.dto.RoomMembersResponse;
import com.korit.clovapi.domain.room.dto.RoomSummariesResponse;
import com.korit.clovapi.domain.room.dto.StatusMessageRequest;
import com.korit.clovapi.domain.room.dto.StatusMessageResponse;
import com.korit.clovapi.domain.room.dto.UpdateRoomRequest;
import com.korit.clovapi.domain.room.service.RoomService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public ApiResponse<RoomSummariesResponse> findMyRooms(Authentication authentication) {
        return ApiResponse.success(roomService.findMyRooms(currentUserId(authentication)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoomDetailResponse>> create(
            Authentication authentication,
            @Valid @RequestBody CreateRoomRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(roomService.create(currentUserId(authentication), request)));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomDetailResponse> findDetail(Authentication authentication, @PathVariable long roomId) {
        return ApiResponse.success(roomService.findDetail(roomId, currentUserId(authentication)));
    }

    @PatchMapping("/{roomId}")
    public ApiResponse<RoomDetailResponse> update(
            Authentication authentication,
            @PathVariable long roomId,
            @Valid @RequestBody UpdateRoomRequest request
    ) {
        return ApiResponse.success(roomService.update(roomId, currentUserId(authentication), request));
    }

    @GetMapping("/{roomId}/members")
    public ApiResponse<RoomMembersResponse> findMembers(Authentication authentication, @PathVariable long roomId) {
        return ApiResponse.success(roomService.findMembers(roomId, currentUserId(authentication)));
    }

    @DeleteMapping("/{roomId}/members/me")
    public ApiResponse<Void> leave(Authentication authentication, @PathVariable long roomId) {
        roomService.leave(roomId, currentUserId(authentication));
        return ApiResponse.success(null);
    }

    @PatchMapping("/{roomId}/members/me/status-message")
    public ApiResponse<StatusMessageResponse> updateStatusMessage(
            Authentication authentication,
            @PathVariable long roomId,
            @Valid @RequestBody StatusMessageRequest request
    ) {
        return ApiResponse.success(roomService.updateStatusMessage(roomId, currentUserId(authentication), request));
    }

    @PatchMapping("/{roomId}/favorite")
    public ApiResponse<FavoriteResponse> updateFavorite(
            Authentication authentication,
            @PathVariable long roomId,
            @Valid @RequestBody FavoriteRequest request
    ) {
        return ApiResponse.success(roomService.updateFavorite(roomId, currentUserId(authentication), request));
    }

    @PostMapping("/{roomId}/revive")
    public ApiResponse<RoomDetailResponse> revive(Authentication authentication, @PathVariable long roomId) {
        return ApiResponse.success(roomService.revive(roomId, currentUserId(authentication)));
    }

    private long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
