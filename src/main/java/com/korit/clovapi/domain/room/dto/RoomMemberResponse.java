package com.korit.clovapi.domain.room.dto;

import com.korit.clovapi.domain.room.entity.RoomMember;

import java.time.LocalDateTime;

public record RoomMemberResponse(
        String membershipId, String userId, String nickname, String profileImageUrl,
        String status, String statusMessage, LocalDateTime joinedAt, LocalDateTime leftAt,
        String birthMonthDay
) {
    public static RoomMemberResponse from(RoomMember member) {
        return new RoomMemberResponse(String.valueOf(member.getId()), String.valueOf(member.getUserId()),
                member.getNickname(), member.getProfileImageUrl(), member.getStatus(), member.getStatusMessage(),
                member.getJoinedAt(), member.getLeftAt(), member.getBirthMonthDay());
    }
}
