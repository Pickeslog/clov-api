package com.korit.clovapi.domain.user.dto;

import com.korit.clovapi.domain.auth.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserProfileResponse(
        String id, String email, String nickname, String profileImageUrl,
        LocalDate birthdate, String personalInviteCode, Boolean isSocial, LocalDateTime createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                String.valueOf(user.getId()), user.getEmail(), user.getNickname(), user.getProfileImageUrl(),
                user.getBirthdate(), user.getPersonalInviteCode(), user.getOauthProvider() != null, user.getCreatedAt()
        );
    }
}
