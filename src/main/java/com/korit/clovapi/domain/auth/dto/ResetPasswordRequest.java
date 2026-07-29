package com.korit.clovapi.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank
        @Pattern(
                // 계약 §4-1 비밀번호 정책 — SignupRequest·ChangePasswordRequest와 동일해야 한다.
                regexp = "^(?=.{8,20}$)(?:(?=.*[A-Za-z])(?=.*\\d)|(?=.*[A-Za-z])(?=.*[\\p{Punct}])|(?=.*\\d)(?=.*[\\p{Punct}]))[A-Za-z\\d\\p{Punct}]+$",
                message = "비밀번호는 8~20자의 영문, 숫자, 특수문자 중 2종 이상이어야 합니다."
        )
        String newPassword
) {
}
