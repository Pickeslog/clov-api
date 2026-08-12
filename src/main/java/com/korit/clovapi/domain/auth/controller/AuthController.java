package com.korit.clovapi.domain.auth.controller;

import com.korit.clovapi.domain.auth.dto.AuthResponse;
import com.korit.clovapi.domain.auth.dto.ForgotPasswordRequest;
import com.korit.clovapi.domain.auth.dto.LoginRequest;
import com.korit.clovapi.domain.auth.dto.OAuthConsentRequest;
import com.korit.clovapi.domain.auth.dto.OAuthExchangeRequest;
import com.korit.clovapi.domain.auth.dto.OAuthExchangeResponse;
import com.korit.clovapi.domain.auth.dto.OAuthLinkConfirmRequest;
import com.korit.clovapi.domain.auth.dto.PasswordResetTokenStatusResponse;
import com.korit.clovapi.domain.auth.dto.RefreshTokenRequest;
import com.korit.clovapi.domain.auth.dto.ResetPasswordRequest;
import com.korit.clovapi.domain.auth.dto.SignupRequest;
import com.korit.clovapi.domain.auth.dto.TokenResponse;
import com.korit.clovapi.domain.auth.service.AuthService;
import com.korit.clovapi.domain.auth.service.OAuthAuthService;
import com.korit.clovapi.domain.auth.service.PasswordResetService;
import com.korit.clovapi.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final OAuthAuthService oauthAuthService;
    private final PasswordResetService passwordResetService;

    public AuthController(
            AuthService authService,
            OAuthAuthService oauthAuthService,
            PasswordResetService passwordResetService
    ) {
        this.authService = authService;
        this.oauthAuthService = oauthAuthService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(authService.signup(request)));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ApiResponse.success(null);
    }

    /** 계약 §4-4. 계정 유무·소셜 전용 여부와 무관하게 항상 200이다. */
    @PostMapping("/password/forgot")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgot(request);
        return ApiResponse.success(null);
    }

    /** 재설정 화면 진입 시 토큰 유효성 확인. 무효하면 400 PASSWORD_RESET_TOKEN_INVALID. */
    @GetMapping("/password/reset")
    public ApiResponse<PasswordResetTokenStatusResponse> verifyPasswordResetToken(@RequestParam String token) {
        passwordResetService.verifyToken(token);
        return ApiResponse.success(new PasswordResetTokenStatusResponse(true));
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/oauth/exchange")
    public ApiResponse<OAuthExchangeResponse> exchange(@Valid @RequestBody OAuthExchangeRequest request) {
        return ApiResponse.success(oauthAuthService.exchange(request.code()));
    }

    @PostMapping("/oauth/consent")
    public ApiResponse<AuthResponse> consent(@Valid @RequestBody OAuthConsentRequest request) {
        return ApiResponse.success(oauthAuthService.consent(request));
    }

    @PostMapping("/oauth/link-confirm")
    public ApiResponse<AuthResponse> linkConfirm(@Valid @RequestBody OAuthLinkConfirmRequest request) {
        return ApiResponse.success(oauthAuthService.linkConfirm(request));
    }
}
