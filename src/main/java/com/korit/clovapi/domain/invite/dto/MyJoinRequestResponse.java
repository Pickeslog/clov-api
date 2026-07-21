package com.korit.clovapi.domain.invite.dto;

import com.korit.clovapi.domain.invite.entity.RoomJoinRequest;

import java.time.LocalDateTime;

/**
 * 내가 보낸 가입 신청(요청한 방) 한 건. {@code status}=PENDING/REJECTED,
 * {@code roomStatus}로 사라진 방(방 삭제) 구분.
 */
public record MyJoinRequestResponse(
        String id, String roomId, String roomName, String status, String roomStatus, LocalDateTime requestedAt
) {
    public static MyJoinRequestResponse from(RoomJoinRequest request) {
        return new MyJoinRequestResponse(
                String.valueOf(request.getId()),
                String.valueOf(request.getRoomId()),
                request.getRoomName(),
                request.getStatus(),
                request.getRoomStatus(),
                request.getRequestedAt()
        );
    }
}
