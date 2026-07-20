package com.korit.clovapi.domain.auth.dto;

import com.korit.clovapi.domain.auth.entity.User;

import java.time.LocalDate;

public record UserResponse(
        String id,
        String email,
        String nickname,
        String profileImageUrl,
        LocalDate birthdate,
        String personalInviteCode
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getBirthdate(),
                user.getPersonalInviteCode()
        );
    }
}
