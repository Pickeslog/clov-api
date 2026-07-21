package com.korit.clovapi.domain.notification.controller;

import com.korit.clovapi.domain.notification.dto.NotificationResponse;
import com.korit.clovapi.domain.notification.service.NotificationService;
import com.korit.clovapi.global.response.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
    
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/rooms/{roomId}/notifications")
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @PathVariable Long roomId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        Long requesterId = (Long) authentication.getPrincipal();
        return ApiResponse.success(notificationService.getNotifications(roomId, requesterId, type, page, size));
    }

    @PatchMapping("/notifications/{id}/read")
    public ApiResponse<Void> markAsRead(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long requesterId = (Long) authentication.getPrincipal();
        notificationService.markAsRead(id, requesterId);
        return ApiResponse.success(null);
    }

    @PatchMapping("/rooms/{roomId}/notifications/read-all")
    public ApiResponse<Void> markAllAsRead(
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        Long requesterId = (Long) authentication.getPrincipal();
        notificationService.markAllAsRead(roomId, requesterId);
        return ApiResponse.success(null);
    }
}
