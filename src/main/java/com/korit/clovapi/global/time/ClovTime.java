package com.korit.clovapi.global.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 서비스가 말하는 "하루"의 기준.
 *
 * <p>시각의 저장·비교는 전부 UTC다(application.yaml의 {@code SET time_zone = '+00:00'},
 * 각 서비스의 {@code LocalDateTime.now(ZoneOffset.UTC)}). 그 관례는 그대로 두고,
 * "오늘 몇 회" 같은 <b>달력상의 하루 경계</b>만 사용자 지역 기준으로 잡기 위한 유틸이다.
 *
 * <p>UTC 자정으로 자르면 한국 사용자에게는 날짜가 오전 9시에 바뀐다.
 * 자정을 넘겨 날짜가 바뀌었는데도 일일 한도가 리셋되지 않는 문제가 그래서 생긴다.
 */
public final class ClovTime {

    /** 서비스 기준 지역. 단일 지역 서비스라 상수로 둔다. */
    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");

    private ClovTime() {
    }

    /**
     * 오늘({@link #APP_ZONE} 기준) 0시에 해당하는 UTC 시각.
     * DB의 UTC 컬럼과 그대로 비교할 수 있다.
     */
    public static LocalDateTime startOfTodayUtc() {
        return startOfDayUtc(LocalDate.now(APP_ZONE));
    }

    /**
     * 주어진 날짜({@link #APP_ZONE} 기준) 0시에 해당하는 UTC 시각.
     * 예: KST 2026-07-25 00:00 → UTC 2026-07-24 15:00.
     */
    public static LocalDateTime startOfDayUtc(LocalDate date) {
        return date.atStartOfDay(APP_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
