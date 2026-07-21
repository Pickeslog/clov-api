package com.korit.clovapi.domain.invite.dto;

import com.korit.clovapi.domain.invite.entity.RoomJoinRequest;

import java.time.LocalDateTime;

public record JoinRequestListItemResponse(
        String id, String roomId, ApplicantResponse applicant, String status, LocalDateTime requestedAt
) {
    public static JoinRequestListItemResponse from(RoomJoinRequest request) {
        return new JoinRequestListItemResponse(String.valueOf(request.getId()), String.valueOf(request.getRoomId()),
                new ApplicantResponse(String.valueOf(request.getUserId()), request.getNickname(), request.getProfileImageUrl()),
                request.getStatus(), request.getRequestedAt());
    }

    public record ApplicantResponse(String id, String nickname, String profileImageUrl) {
    }
}
