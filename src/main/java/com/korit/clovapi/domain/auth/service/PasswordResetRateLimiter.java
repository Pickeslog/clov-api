package com.korit.clovapi.domain.auth.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 재설정 메일 요청 속도 제한(계약 §14 {@code RATE_LIMITED}).
 *
 * <p><b>계정 존재 여부와 무관하게</b> 이메일 문자열만으로 센다. 존재하는 계정만 제한하면
 * "제한에 걸렸다 = 그 계정이 있다"가 되어 계약 §4-4의 계정 열거 방지가 무너진다.
 *
 * <p>인메모리다. 현재 단일 인스턴스 배포라 충분하고, 다중 인스턴스가 되면 저장소를
 * 공유 캐시로 바꿔야 한다(그때 이 클래스만 갈면 된다).
 */
@Component
public class PasswordResetRateLimiter {

    private static final int MAX_REQUESTS = 3;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    /** 죽은 항목이 쌓이지 않게 이 크기를 넘으면 지나간 창을 훑어 지운다. */
    private static final int SWEEP_THRESHOLD = 1024;

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /** 허용되면 true. 호출 자체가 1회로 집계되므로 통과한 요청만 창에 남는다. */
    public boolean tryAcquire(String email, Instant now) {
        if (hits.size() > SWEEP_THRESHOLD) {
            sweep(now);
        }
        Deque<Instant> window = hits.computeIfAbsent(normalize(email), key -> new ArrayDeque<>());
        synchronized (window) {
            Instant cutoff = now.minus(WINDOW);
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= MAX_REQUESTS) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    private void sweep(Instant now) {
        Instant cutoff = now.minus(WINDOW);
        hits.values().removeIf(window -> {
            synchronized (window) {
                return window.isEmpty() || window.peekLast().isBefore(cutoff);
            }
        });
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
