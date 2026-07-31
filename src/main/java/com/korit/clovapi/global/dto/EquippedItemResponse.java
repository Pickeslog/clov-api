package com.korit.clovapi.global.dto;

// 마스코트에 장착 중인 상점 아이템 요약. user 도메인(preferences)과 shop 도메인(장착/해제 응답)에서
// 공유하는 읽기 모델이라 global 하위에 둔다(MyBatis type-aliases-package 스캔 대상 밖).
public record EquippedItemResponse(String itemId, String name, String imageUrl) {
}
