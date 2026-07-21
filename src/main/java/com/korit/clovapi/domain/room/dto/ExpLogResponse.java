package com.korit.clovapi.domain.room.dto;

import com.korit.clovapi.domain.room.entity.FriendshipExpLog;

import java.time.LocalDateTime;

public record ExpLogResponse(
        String id, String actionType, Integer expDelta, TriggeredByResponse triggeredBy,
        String referenceId, LocalDateTime createdAt
) {
    public static ExpLogResponse from(FriendshipExpLog log) {
        return new ExpLogResponse(String.valueOf(log.getId()), log.getActionType(), log.getExpDelta(),
                new TriggeredByResponse(String.valueOf(log.getTriggeredBy()), log.getNickname(), log.getProfileImageUrl()),
                log.getReferenceId() == null ? null : String.valueOf(log.getReferenceId()), log.getCreatedAt());
    }

    public record TriggeredByResponse(String id, String nickname, String profileImageUrl) {
    }
}
