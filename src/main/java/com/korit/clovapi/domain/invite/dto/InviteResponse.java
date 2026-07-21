package com.korit.clovapi.domain.invite.dto;

import com.korit.clovapi.domain.invite.entity.RoomInvite;

import java.time.LocalDateTime;

public record InviteResponse(String id, String inviteCode, String status, LocalDateTime expiresAt, LocalDateTime createdAt) {
    public static InviteResponse from(RoomInvite invite) {
        return new InviteResponse(String.valueOf(invite.getId()), invite.getInviteCode(), invite.getStatus(),
                invite.getExpiresAt(), invite.getCreatedAt());
    }
}
