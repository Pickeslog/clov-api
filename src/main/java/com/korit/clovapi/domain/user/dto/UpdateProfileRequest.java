package com.korit.clovapi.domain.user.dto;
import java.time.LocalDate;
public record UpdateProfileRequest(String nickname, String profileImageUrl, LocalDate birthdate) {}
