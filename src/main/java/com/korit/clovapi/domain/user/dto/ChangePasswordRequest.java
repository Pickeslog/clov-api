package com.korit.clovapi.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank
        @Pattern(
                regexp = "^(?=.{8,20}$)(?:(?=.*[A-Za-z])(?=.*\\d)|(?=.*[A-Za-z])(?=.*[\\p{Punct}])|(?=.*\\d)(?=.*[\\p{Punct}]))[A-Za-z\\d\\p{Punct}]+$",
                message = "비밀번호는 8~20자의 영문, 숫자, 특수문자 중 2종 이상이어야 합니다."
        )
        String newPassword
) {
}
