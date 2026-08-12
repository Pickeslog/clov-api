package com.korit.clovapi.domain.shop.dto;

import com.korit.clovapi.domain.shop.entity.WalletTransaction;

import java.time.LocalDateTime;

/**
 * 원장(wallet_transactions) 한 줄. reason은 가공하지 않고 원문 그대로 노출한다 —
 * category/rarity처럼 프론트가 라벨을 매핑하는 기존 관례와 일관되고, 화면 문구가
 * 바뀌어도 백엔드를 다시 배포할 필요가 없다.
 */
public record WalletTransactionResponse(
        String id,
        String reason,
        long amount,
        long balanceAfter,
        String referenceId,
        LocalDateTime createdAt
) {
    public static WalletTransactionResponse from(WalletTransaction transaction) {
        return new WalletTransactionResponse(
                String.valueOf(transaction.getId()),
                transaction.getReason(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getReferenceId() == null ? null : String.valueOf(transaction.getReferenceId()),
                transaction.getCreatedAt()
        );
    }
}
