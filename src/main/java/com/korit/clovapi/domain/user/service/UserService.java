package com.korit.clovapi.domain.user.service;

import com.korit.clovapi.domain.auth.entity.User;
import com.korit.clovapi.domain.auth.mapper.UserMapper;
import com.korit.clovapi.domain.user.dto.ChangePasswordRequest;
import com.korit.clovapi.domain.user.dto.PreferencesResponse;
import com.korit.clovapi.domain.user.dto.UpdatePreferencesRequest;
import com.korit.clovapi.domain.user.dto.UpdateProfileRequest;
import com.korit.clovapi.domain.user.dto.UserProfileResponse;
import com.korit.clovapi.domain.user.entity.UserPreference;
import com.korit.clovapi.domain.user.mapper.UserPreferenceMapper;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import com.korit.clovapi.global.security.refresh.RefreshTokenMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final UserPreferenceMapper preferenceMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, UserPreferenceMapper preferenceMapper,
                       RefreshTokenMapper refreshTokenMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.preferenceMapper = preferenceMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserProfileResponse findMyProfile(long userId) {
        return UserProfileResponse.from(findUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(long userId, UpdateProfileRequest request) {
        userMapper.updateProfile(userId, request);
        return findMyProfile(userId);
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (user.getPassword() == null || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new DomainException(ErrorCode.INVALID_CREDENTIALS);
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
        refreshTokenMapper.revokeAllByUserId(userId);
    }

    @Transactional
    public void anonymize(long userId) {
        findUser(userId);
        userMapper.anonymize(userId);
        refreshTokenMapper.revokeAllByUserId(userId);
    }

    @Transactional
    public PreferencesResponse preferences(long userId) {
        UserPreference preference = preferenceMapper.findByUserId(userId)
                .orElseGet(() -> {
                    preferenceMapper.insertDefault(userId);
                    return preferenceMapper.findByUserId(userId).orElseThrow();
                });
        return PreferencesResponse.from(preference);
    }

    @Transactional
    public PreferencesResponse updatePreferences(long userId, UpdatePreferencesRequest request) {
        preferences(userId); // 기본값 row 보장(최초 접근 시 lazy insert)
        preferenceMapper.update(userId, request);
        return preferences(userId);
    }

    private User findUser(long userId) {
        return userMapper.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getAnonymized()))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }
}
