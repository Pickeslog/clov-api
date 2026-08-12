package com.korit.clovapi.domain.room.dto;

import com.korit.clovapi.domain.room.entity.Room;
import com.korit.clovapi.domain.room.entity.RoomMember;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RoomSummaryResponse(
        String id,
        String name,
        String description,
        String themeColor,
        String transportType,
        String coverPhotoUrl,
        Integer friendshipLevel,
        Integer memberCount,
        List<MemberAvatar> memberAvatars,
        Boolean isFavorite,
        String status,
        LocalDateTime createdAt,
        NextPlan nextPlan
) {
    public record NextPlan(String title, LocalDate planDate) {
    }

    // 계약 §4-3·§6(clov-api#141) — status=ACTIVE만, joinedAt 오름차순(호출부가 그렇게 정렬해 넘긴다).
    // 개수 상한은 별도로 두지 않는다 — 방 정원(계약 §2, 8명)이 이미 room_members 자체의 상한이다.
    public record MemberAvatar(String userId, String nickname, String profileImageUrl) {
        public static MemberAvatar from(RoomMember member) {
            return new MemberAvatar(String.valueOf(member.getUserId()), member.getNickname(), member.getProfileImageUrl());
        }
    }

    public static RoomSummaryResponse from(Room room, List<MemberAvatar> memberAvatars) {
        NextPlan nextPlan = room.getNextPlanDate() != null
                ? new NextPlan(room.getNextPlanTitle(), room.getNextPlanDate())
                : null;
        return new RoomSummaryResponse(
                String.valueOf(room.getId()),
                room.getName(),
                room.getDescription(),
                room.getThemeColor(),
                room.getTransportType(),
                room.getCoverPhotoUrl(),
                room.getFriendshipLevel(),
                room.getMemberCount(),
                memberAvatars,
                room.getFavorite(),
                room.getStatus(),
                room.getCreatedAt(),
                nextPlan
        );
    }
}
