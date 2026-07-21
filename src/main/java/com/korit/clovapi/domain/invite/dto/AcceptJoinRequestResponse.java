package com.korit.clovapi.domain.invite.dto;

import java.time.LocalDateTime;

public record AcceptJoinRequestResponse(String membershipId, String roomId, String userId, LocalDateTime undoDeadlineAt) {
}
