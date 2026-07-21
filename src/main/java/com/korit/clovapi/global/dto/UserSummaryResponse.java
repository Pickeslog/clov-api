package com.korit.clovapi.global.dto;

// 공통 사용자 요약 읽기 모델(계약 §4-3). 여러 도메인 응답에서 재사용한다.
// global 하위(domain 밖)라 MyBatis type-aliases-package 스캔 대상이 아니어서 simpleName 별칭 충돌이 없다.
public record UserSummaryResponse(String id, String nickname, String profileImageUrl) {
}
