package com.korit.clovapi.domain.shop.dto;

import java.util.List;

/**
 * #135 — 골드가 왜 0인지 화면에서 확인할 방법이 없던 문제. 목록과 함께 "오늘 얼마나
 * 벌었나·상한에 얼마나 남았나"를 같이 준다(계약 §15-4).
 */
public record ShopTransactionsResponse(
        List<WalletTransactionResponse> items,
        long earnedToday,
        long dailyCap,
        long remaining
) {
}
