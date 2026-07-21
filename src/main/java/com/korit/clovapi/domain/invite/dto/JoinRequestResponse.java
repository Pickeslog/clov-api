package com.korit.clovapi.domain.invite.dto;

import com.korit.clovapi.domain.invite.entity.RoomJoinRequest;

import java.time.LocalDateTime;

public record JoinRequestResponse(String id, String roomId, String status, LocalDateTime requestedAt) {
    public static JoinRequestResponse from(RoomJoinRequest request) {
        return new JoinRequestResponse(String.valueOf(request.getId()), String.valueOf(request.getRoomId()),
                request.getStatus(), request.getRequestedAt());
    }
}
