package com.korit.clovapi.domain.user.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 사용자 설정 부분 수정 요청 (계약 §5).
 *
 * <p>허용값이 정해진 필드는 여기서 막는다. 검증이 없으면 표에 없는 문자열이 그대로 저장되는데,
 * <b>에러가 나지 않고 화면에서만 조용히 깨진다</b> — 프론트는 아는 값이 아니면 기본값으로
 * 떨어뜨리므로, 마스코트나 테마가 이유 없이 되돌아간 것처럼 보인다. 컬럼 길이(VARCHAR(20) 등)
 * 말고는 막는 것이 없었다.
 *
 * <p><b>새 값을 추가할 때는 계약 §5의 허용값 표와 이 정규식을 함께 고친다.</b> 한쪽만 고치면
 * 프론트에서 고를 수 있는 값이 저장에서 400으로 튕긴다(추억 제목 40자에서 실제로 겪은 형태).
 *
 * <p>{@code null}은 통과한다 — 보낸 필드만 반영하는 부분 수정이라 "안 보냄"과 "잘못된 값"을
 * 구분해야 한다.
 */
public record UpdatePreferencesRequest(
        Boolean darkMode,
        String customColor,
        String wallpaperIcon,
        String dashboardBackground,

        @Pattern(regexp = "postbox|giftbox", message = "letterTheme 허용값이 아닙니다(계약 §5)")
        String letterTheme,

        @Pattern(regexp = "stack|clothesline|diary", message = "memoryCardTheme 허용값이 아닙니다(계약 §5)")
        String memoryCardTheme,

        // rob은 프로덕션·DB·프론트가 쓰는 값이다. 프로토타입 위젯(croby-mascot.js)의 'robot'과
        // 다르며, 계약에 robot으로 잘못 적혀 있던 것을 2026-07-31에 정정했다.
        @Pattern(regexp = "crobi|rob|burgerOldman|takoGun|kimCheolsu", message = "mascotType 허용값이 아닙니다(계약 §5)")
        String mascotType
) {
}
