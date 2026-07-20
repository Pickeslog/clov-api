package com.korit.clovapi.domain.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank
        @Pattern(
                regexp = "^(?=.{8,20}$)(?:(?=.*[A-Za-z])(?=.*\\d)|(?=.*[A-Za-z])(?=.*[\\p{Punct}])|(?=.*\\d)(?=.*[\\p{Punct}]))[A-Za-z\\d\\p{Punct}]+$",
                message = "비밀번호는 8~20자의 영문, 숫자, 특수문자 중 2종 이상이어야 합니다."
        )
        String password,
        @NotBlank @Size(max = 50) String nickname,
        @Past LocalDate birthdate,
        @NotNull @Valid AgreementsRequest agreements
) {
}
