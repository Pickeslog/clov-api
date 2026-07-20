package com.korit.clovapi.domain.auth.service;

import com.korit.clovapi.domain.auth.dto.AuthResponse;
import com.korit.clovapi.domain.auth.dto.AgreementsRequest;
import com.korit.clovapi.domain.auth.dto.LoginRequest;
import com.korit.clovapi.domain.auth.dto.RefreshTokenRequest;
import com.korit.clovapi.domain.auth.dto.SignupRequest;
import com.korit.clovapi.domain.auth.dto.TokenResponse;
import com.korit.clovapi.domain.auth.dto.UserResponse;
import com.korit.clovapi.domain.auth.entity.User;
import com.korit.clovapi.domain.auth.mapper.UserMapper;
import com.korit.clovapi.domain.auth.oauth.OAuthProfile;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import com.korit.clovapi.global.security.jwt.JwtClaims;
import com.korit.clovapi.global.security.jwt.JwtTokenProvider;
import com.korit.clovapi.global.security.jwt.TokenType;
import com.korit.clovapi.global.security.refresh.RefreshToken;
import com.korit.clovapi.global.security.refresh.RefreshTokenMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class AuthService {

    private static final char[] BASE32 = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserMapper userMapper,
            RefreshTokenMapper refreshTokenMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (!request.agreements().service() || !request.agreements().privacy()) {
            throw new DomainException(ErrorCode.TERMS_REQUIRED);
        }
        if (userMapper.findByEmail(request.email()).isPresent()) {
            throw new DomainException(ErrorCode.EMAIL_DUPLICATED);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setBirthdate(request.birthdate());
        user.setTermsAgreedAt(now);
        user.setPrivacyAgreedAt(now);
        user.setMarketingAgreedAt(request.agreements().marketing() ? now : null);
        user.setPersonalInviteCode(nextPersonalInviteCode());
        userMapper.insert(user);

        return authenticate(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userMapper.findByEmail(request.email())
                .filter(found -> found.getPassword() != null)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_CREDENTIALS));

        return authenticate(user);
    }

    @Transactional
    public AuthResponse signupOAuth(OAuthProfile profile, AgreementsRequest agreements) {
        if (userMapper.findByEmail(profile.email()).isPresent()) {
            throw new DomainException(ErrorCode.EMAIL_DUPLICATED);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        User user = new User();
        user.setEmail(profile.email());
        user.setPassword(null);
        user.setOauthProvider(profile.provider());
        user.setOauthSubject(profile.subject());
        user.setNickname(profile.nickname());
        user.setTermsAgreedAt(now);
        user.setPrivacyAgreedAt(now);
        user.setMarketingAgreedAt(agreements.marketing() ? now : null);
        user.setPersonalInviteCode(nextPersonalInviteCode());
        userMapper.insert(user);
        return authenticate(user);
    }

    public AuthResponse authenticate(User user) {
        TokenResponse tokens = issueTokens(user.getId());
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), UserResponse.from(user));
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        JwtClaims claims = parseRefreshToken(request.refreshToken());
        String tokenHash = hash(request.refreshToken());
        RefreshToken storedToken = refreshTokenMapper.findValidByTokenHash(tokenHash, LocalDateTime.now(ZoneOffset.UTC))
                .orElseThrow(() -> new DomainException(ErrorCode.TOKEN_EXPIRED));

        if (storedToken.getUserId() != claims.userId() || refreshTokenMapper.revokeByTokenHash(tokenHash) != 1) {
            throw new DomainException(ErrorCode.TOKEN_EXPIRED);
        }
        return issueTokens(claims.userId());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        parseRefreshToken(request.refreshToken());
        String tokenHash = hash(request.refreshToken());
        refreshTokenMapper.findValidByTokenHash(tokenHash, LocalDateTime.now(ZoneOffset.UTC))
                .orElseThrow(() -> new DomainException(ErrorCode.TOKEN_EXPIRED));

        if (refreshTokenMapper.revokeByTokenHash(tokenHash) != 1) {
            throw new DomainException(ErrorCode.TOKEN_EXPIRED);
        }
    }

    private TokenResponse issueTokens(long userId) {
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        JwtClaims refreshClaims = jwtTokenProvider.parse(refreshToken);

        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hash(refreshToken));
        token.setExpiresAt(LocalDateTime.ofInstant(refreshClaims.expiresAt(), ZoneOffset.UTC));
        refreshTokenMapper.insert(token);

        return new TokenResponse(accessToken, refreshToken);
    }

    private JwtClaims parseRefreshToken(String refreshToken) {
        try {
            JwtClaims claims = jwtTokenProvider.parse(refreshToken);
            if (claims.tokenType() != TokenType.REFRESH) {
                throw new DomainException(ErrorCode.INVALID_TOKEN);
            }
            return claims;
        } catch (ExpiredJwtException exception) {
            throw new DomainException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new DomainException(ErrorCode.INVALID_TOKEN);
        }
    }

    private String nextPersonalInviteCode() {
        String inviteCode;
        do {
            StringBuilder suffix = new StringBuilder(6);
            for (int index = 0; index < 6; index++) {
                suffix.append(BASE32[RANDOM.nextInt(BASE32.length)]);
            }
            inviteCode = "CLV-" + suffix;
        } while (userMapper.existsByPersonalInviteCode(inviteCode));
        return inviteCode;
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
