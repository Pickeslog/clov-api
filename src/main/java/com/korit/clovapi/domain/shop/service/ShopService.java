package com.korit.clovapi.domain.shop.service;

import com.korit.clovapi.domain.shop.dto.PurchaseResponse;
import com.korit.clovapi.domain.shop.dto.ShopItemResponse;
import com.korit.clovapi.domain.shop.dto.ShopItemsResponse;
import com.korit.clovapi.domain.shop.dto.WalletResponse;
import com.korit.clovapi.domain.shop.entity.ShopItem;
import com.korit.clovapi.domain.shop.entity.UserWallet;
import com.korit.clovapi.domain.shop.entity.WalletTransaction;
import com.korit.clovapi.domain.shop.mapper.ShopMapper;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상점(shop) 도메인 — DOMAIN-NAMING-REGISTRY/API-CONTRACT SSOT에 아직 등록되지 않은
 * 신규 도메인이다. 프론트 PR #111(feat/96-shop-ui)이 이미 이 계약으로 병합 대기 중이고,
 * 개발 DB(st4_clov)에 이미 이 스키마와 테스트 데이터가 존재해 그 실스키마를 그대로 따른다
 * (shop_items.code/status/sort_order, user_wallets.balance, user_inventory_items.paid_price,
 * wallet_transactions 원장). 정식 반영 시 SSOT 등록이 먼저 필요하다.
 */
@Service
public class ShopService {

    /** 지갑 최초 생성 시 지급하는 시작 골드. 기존 원장 데이터(SIGNUP_GRANT=20000)와 맞춘 값. */
    static final long SIGNUP_GRANT_AMOUNT = 20000;

    private final ShopMapper shopMapper;

    public ShopService(ShopMapper shopMapper) {
        this.shopMapper = shopMapper;
    }

    @Transactional(readOnly = true)
    public ShopItemsResponse findCatalog(long userId, String category, String rarity) {
        String normalizedCategory = normalize(category);
        String normalizedRarity = normalize(rarity);
        return ShopItemsResponse.from(shopMapper.findCatalog(userId, normalizedCategory, normalizedRarity));
    }

    @Transactional(readOnly = true)
    public ShopItemsResponse findInventory(long userId) {
        return ShopItemsResponse.from(shopMapper.findInventory(userId));
    }

    @Transactional
    public WalletResponse getWallet(long userId) {
        return new WalletResponse(getOrCreateWallet(userId).getBalance());
    }

    @Transactional
    public PurchaseResponse purchase(long userId, long itemId) {
        ShopItem item = shopMapper.findById(itemId)
                .orElseThrow(() -> new DomainException(ErrorCode.SHOP_ITEM_NOT_FOUND));
        if (!item.isPurchasable()) {
            throw new DomainException(ErrorCode.ITEM_NOT_PURCHASABLE);
        }
        if (shopMapper.existsInInventory(userId, itemId)) {
            throw new DomainException(ErrorCode.ITEM_ALREADY_OWNED);
        }

        getOrCreateWallet(userId);
        // 잔액 확정은 읽고-차감이라 동시 구매 시 잔액이 꼬일 수 있어 지갑 행을 잠그고 읽는다.
        UserWallet wallet = shopMapper.findWalletForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("wallet must exist after getOrCreateWallet"));

        long finalPrice = item.finalPrice();
        if (wallet.getBalance() < finalPrice) {
            throw new DomainException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        long newBalance = wallet.getBalance() - finalPrice;
        shopMapper.updateBalance(userId, newBalance);
        shopMapper.insertInventory(userId, itemId, finalPrice);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserId(userId);
        transaction.setReason(WalletTransaction.REASON_PURCHASE);
        transaction.setAmount(-finalPrice);
        transaction.setBalanceAfter(newBalance);
        transaction.setReferenceId(itemId);
        shopMapper.insertTransaction(transaction);

        item.setOwned(true);
        return new PurchaseResponse(ShopItemResponse.from(item), newBalance);
    }

    private UserWallet getOrCreateWallet(long userId) {
        UserWallet existing = shopMapper.findWallet(userId).orElse(null);
        if (existing != null) {
            return existing;
        }

        int inserted = shopMapper.insertWallet(userId, SIGNUP_GRANT_AMOUNT);
        if (inserted > 0) {
            WalletTransaction grant = new WalletTransaction();
            grant.setUserId(userId);
            grant.setReason(WalletTransaction.REASON_SIGNUP_GRANT);
            grant.setAmount(SIGNUP_GRANT_AMOUNT);
            grant.setBalanceAfter(SIGNUP_GRANT_AMOUNT);
            grant.setReferenceId(null);
            shopMapper.insertTransaction(grant);
        }
        return shopMapper.findWallet(userId)
                .orElseThrow(() -> new IllegalStateException("wallet must exist after insertWallet"));
    }

    private String normalize(String value) {
        return (value == null || value.isBlank() || value.equalsIgnoreCase("all")) ? null : value;
    }
}
