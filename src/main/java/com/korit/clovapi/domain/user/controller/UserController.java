package com.korit.clovapi.domain.user.controller;

import com.korit.clovapi.domain.user.dto.ChangePasswordRequest;
import com.korit.clovapi.domain.user.dto.PreferencesResponse;
import com.korit.clovapi.domain.user.dto.UpdatePreferencesRequest;
import com.korit.clovapi.domain.user.dto.UpdateProfileRequest;
import com.korit.clovapi.domain.user.dto.UserProfileResponse;
import com.korit.clovapi.domain.user.service.UserService;
import com.korit.clovapi.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(Authentication authentication) {
        return ApiResponse.success(userService.findMyProfile(currentUserId(authentication)));
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateProfile(Authentication authentication,
                                                          @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateProfile(currentUserId(authentication), request));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(Authentication authentication,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUserId(authentication), request);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> delete(Authentication authentication) {
        userService.anonymize(currentUserId(authentication));
        return ApiResponse.success(null);
    }

    @GetMapping("/me/preferences")
    public ApiResponse<PreferencesResponse> preferences(Authentication authentication) {
        return ApiResponse.success(userService.preferences(currentUserId(authentication)));
    }

    @PatchMapping("/me/preferences")
    public ApiResponse<PreferencesResponse> updatePreferences(Authentication authentication,
                                                              @Valid @RequestBody UpdatePreferencesRequest request) {
        return ApiResponse.success(userService.updatePreferences(currentUserId(authentication), request));
    }

    private long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
