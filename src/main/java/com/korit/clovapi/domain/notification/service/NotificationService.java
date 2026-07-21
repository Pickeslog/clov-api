package com.korit.clovapi.domain.notification.service;

import com.korit.clovapi.domain.notification.dto.NotificationResponse;
import com.korit.clovapi.domain.notification.entity.Notification;
import com.korit.clovapi.domain.notification.mapper.NotificationMapper;
import com.korit.clovapi.domain.room.service.RoomService;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {
    
    private final NotificationMapper notificationMapper;
    private final RoomService roomService;

    public NotificationService(NotificationMapper notificationMapper, RoomService roomService) {
        this.notificationMapper = notificationMapper;
        this.roomService = roomService;
    }

    public List<NotificationResponse> getNotifications(Long roomId, Long requesterId, String type, int page, int size) {
        roomService.assertActiveMember(roomId, requesterId);
        
        int offset = page * size;
        return notificationMapper.getNotifications(roomId, requesterId, type, offset, size)
                .stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markAsRead(Long id, Long requesterId) {
        Notification notification = notificationMapper.findById(id)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        
        if (!notification.getRecipientId().equals(requesterId)) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        
        notificationMapper.markAsRead(id);
    }

    @Transactional
    public void markAllAsRead(Long roomId, Long requesterId) {
        roomService.assertActiveMember(roomId, requesterId);
        
        notificationMapper.markAllAsRead(roomId, requesterId);
    }
}
