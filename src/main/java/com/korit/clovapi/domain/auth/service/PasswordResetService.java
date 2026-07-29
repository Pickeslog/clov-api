package com.korit.clovapi.domain.auth.service;

import com.korit.clovapi.domain.auth.dto.ForgotPasswordRequest;
import com.korit.clovapi.domain.auth.dto.ResetPasswordRequest;
import com.korit.clovapi.domain.auth.entity.PasswordResetToken;
import com.korit.clovapi.domain.auth.entity.User;
import com.korit.clovapi.domain.auth.mapper.PasswordResetTokenMapper;
import com.korit.clovapi.domain.auth.mapper.UserMapper;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import com.korit.clovapi.global.mail.MailSender;
import com.korit.clovapi.global.security.refresh.RefreshTokenMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 비밀번호 재설정(계약 §4-4).
 *
 * <p>핵심 규칙 셋을 코드로 강제한다.
 * <ul>
 *   <li><b>항상 200</b> — 계정 유무·소셜 전용 여부와 무관하게 {@link #forgot}은 예외를 던지지 않는다(속도 제한 제외).</li>
 *   <li><b>1회용 · 최대 1개</b> — 재요청하면 이전 미사용 토큰을 즉시 폐기한다.</li>
 *   <li><b>재설정 성공 시 refresh 전부 revoke</b> — 계정 탈취 복구 시 공격자 세션까지 끊는다.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    /** 계약 §4-4. 메일을 바로 확인하지 못하는 경우까지 감안한 값. */
    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final PasswordResetTokenMapper passwordResetTokenMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final MailSender mailSender;
    private final PasswordResetRateLimiter rateLimiter;
    private final String clientUrl;

    public PasswordResetService(
            UserMapper userMapper,
            PasswordResetTokenMapper passwordResetTokenMapper,
            RefreshTokenMapper refreshTokenMapper,
            PasswordEncoder passwordEncoder,
            MailSender mailSender,
            PasswordResetRateLimiter rateLimiter,
            // 프로필 문서(dev/prod)에만 있는 값이라 test 프로필에서도 뜨도록 기본값을 둔다.
            @Value("${client.url:http://localhost:5173}") String clientUrl
    ) {
        this.userMapper = userMapper;
        this.passwordResetTokenMapper = passwordResetTokenMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.rateLimiter = rateLimiter;
        this.clientUrl = clientUrl;
    }

    /**
     * 재설정 메일 요청. <b>계정이 없든, 소셜 전용이든, 정상이든 똑같이 아무것도 반환하지 않는다.</b>
     * 세 경로의 분기는 오직 "무슨 메일을 보내는가"에만 있다.
     *
     * <p><b>일부러 {@code @Transactional}을 걸지 않았다.</b> 트랜잭션 안에서 메일을 보내면
     * 커밋 전에 비동기 발송이 나가 사용자가 링크를 눌렀을 때 토큰이 아직 안 보일 수 있다.
     * 폐기와 발급이 원자적이지 않아도 문제가 없다 — 발급이 실패하면 유효한 링크가 없는 상태라
     * 사용자가 다시 요청하면 그만이고, 잘못된 링크가 살아남는 방향의 실패는 생기지 않는다.
     */
    public void forgot(ForgotPasswordRequest request) {
        if (!rateLimiter.tryAcquire(request.email(), Instant.now())) {
            throw new DomainException(ErrorCode.RATE_LIMITED);
        }

        Optional<User> found = userMapper.findByEmail(request.email());
        if (found.isEmpty()) {
            return;
        }

        User user = found.get();
        if (user.getPassword() == null) {
            // 소셜 전용 계정 — 재설정 대상이 아니다. 응답으로 알리면 계정 열거가 되므로
            // 수신자 본인만 볼 수 있는 메일에서만 안내한다.
            mailSender.send(user.getEmail(), "[Clov] 비밀번호 재설정 안내", socialAccountBody(user));
            return;
        }

        // 재요청이면 이전 링크를 즉시 죽인다 — 살아 있는 링크는 항상 최대 1개.
        passwordResetTokenMapper.revokeAllActiveByUserId(user.getId());

        String rawToken = generateToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plus(TOKEN_TTL));
        passwordResetTokenMapper.insert(token);

        mailSender.send(user.getEmail(), "[Clov] 비밀번호 재설정", resetBody(rawToken));
    }

    /** 재설정 화면 진입 시 유효성 판정. 무효하면 폼을 띄우지 않고 재요청을 안내하게 한다. */
    @Transactional(readOnly = true)
    public void verifyToken(String rawToken) {
        findValidToken(rawToken);
    }

    @Transactional
    public void reset(ResetPasswordRequest request) {
        PasswordResetToken token = findValidToken(request.token());

        // 소모를 먼저 한다. 영향 행이 0이면 그 사이 다른 요청이 같은 토큰을 썼다는 뜻이라
        // 비밀번호를 바꾸지 않고 실패시킨다(동시 사용 차단).
        if (passwordResetTokenMapper.markUsedByTokenHash(token.getTokenHash()) != 1) {
            throw new DomainException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        userMapper.updatePassword(token.getUserId(), passwordEncoder.encode(request.newPassword()));
        // 계정을 되찾는 경로다 — 공격자가 들고 있을 수 있는 세션을 전부 끊는다.
        refreshTokenMapper.revokeAllByUserId(token.getUserId());
    }

    private PasswordResetToken findValidToken(String rawToken) {
        return passwordResetTokenMapper
                .findValidByTokenHash(hash(rawToken), LocalDateTime.now(ZoneOffset.UTC))
                // 위조·만료·이미 사용됨을 구분하지 않는다(계약 §4-4).
                .orElseThrow(() -> new DomainException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
    }

    private String resetBody(String rawToken) {
        String link = clientUrl + "/reset-password?token=" + rawToken;
        return """
                안녕하세요, Clov입니다.

                아래 링크에서 새 비밀번호를 설정할 수 있습니다.

                %s

                이 링크는 %d시간 동안만 사용할 수 있고, 한 번 사용하면 만료됩니다.
                비밀번호 재설정을 요청하지 않으셨다면 이 메일을 무시하셔도 됩니다.
                """.formatted(link, TOKEN_TTL.toHours());
    }

    private String socialAccountBody(User user) {
        return """
                안녕하세요, Clov입니다.

                이 이메일은 %s 간편 로그인으로 가입된 계정이라 설정된 비밀번호가 없습니다.
                로그인 화면의 간편 로그인 버튼으로 입장해 주세요.

                본인이 요청하지 않으셨다면 이 메일을 무시하셔도 됩니다.
                """.formatted(providerLabel(user.getOauthProvider()));
    }

    private String providerLabel(String provider) {
        if (provider == null) {
            return "소셜";
        }
        return switch (provider.toLowerCase()) {
            case "kakao" -> "카카오";
            case "naver" -> "네이버";
            case "google" -> "구글";
            default -> "소셜";
        };
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        // URL-safe · 패딩 없음 — 메일 링크의 쿼리스트링에 그대로 실린다.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** AuthService의 refresh 토큰 해시와 같은 방식(SHA-256 hex). 원문은 DB에 남기지 않는다. */
    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
