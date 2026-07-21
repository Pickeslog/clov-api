package com.korit.clovapi.domain.user.dto;
import com.korit.clovapi.domain.auth.entity.User; import java.time.*;
public record UserProfileResponse(String id,String email,String nickname,String profileImageUrl,LocalDate birthdate,String personalInviteCode,Boolean isSocial,LocalDateTime createdAt){public static UserProfileResponse from(User u){return new UserProfileResponse(String.valueOf(u.getId()),u.getEmail(),u.getNickname(),u.getProfileImageUrl(),u.getBirthdate(),u.getPersonalInviteCode(),u.getOauthProvider()!=null,u.getCreatedAt());}}
