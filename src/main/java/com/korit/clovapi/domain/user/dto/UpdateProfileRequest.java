package com.korit.clovapi.domain.user.dto;

import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public record UpdateProfileRequest(String nickname, String profileImageUrl, @Past LocalDate birthdate) {}
