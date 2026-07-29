package com.korit.clovapi.domain.shop.service;

import com.korit.clovapi.domain.shop.dto.PurchaseResponse;
import com.korit.clovapi.domain.shop.dto.ShopItemResponse;
import com.korit.clovapi.domain.shop.dto.ShopItemsResponse;
import com.korit.clovapi.domain.shop.dto.WalletResponse;
import com.korit.clovapi.domain.shop.entity.ShopItem;
import com.korit.clovapi.domain.shop.entity.UserWallet;
import com.korit.clovapi.domain.shop.mapper.ShopMapper;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상점(shop) 도메인 — DOMAIN-NAMING-REGISTRY/API-CONTRACT SSOT에 아직 등록되지 않은
 * 신규 도메인이다. 프론트 PR #111(feat/96-shop-ui)이 이미 이 계약으로 병합 대기 중이라
 * 그에 맞춰 뒤따라 구현했다 — 정식 반영 시 SSOT 등록이 먼저 필요하다.
 */
@Service
public class ShopService {

    /** 골드 획득 동선이 아직 없어(추후 XP/마스코트 연동 예정), 지갑 최초 생성 시 지급하는 시작 골드. */
    static final long STARTING_BALANCE = 1000;

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
        return new WalletResponse(getOrCreateWallet(userId).getGoldBalance());
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

        // 잔액 확정은 읽고-차감이라 동시 구매 시 잔액이 꼬일 수 있어 지갑 행을 잠그고 읽는다.
        ensureWalletExists(userId);
        UserWallet wallet = shopMapper.findWalletForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("wallet must exist after ensureWalletExists"));

        long finalPrice = item.finalPrice();
        if (wallet.getGoldBalance() < finalPrice) {
            throw new DomainException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        long newBalance = wallet.getGoldBalance() - finalPrice;
        shopMapper.updateBalance(userId, newBalance);
        shopMapper.insertInventory(userId, itemId);

        item.setOwned(true);
        return new PurchaseResponse(ShopItemResponse.from(item), newBalance);
    }

    private UserWallet getOrCreateWallet(long userId) {
        ensureWalletExists(userId);
        return shopMapper.findWallet(userId)
                .orElseThrow(() -> new IllegalStateException("wallet must exist after ensureWalletExists"));
    }

    private void ensureWalletExists(long userId) {
        if (shopMapper.findWallet(userId).isEmpty()) {
            shopMapper.insertWallet(userId, STARTING_BALANCE);
        }
    }

    private String normalize(String value) {
        return (value == null || value.isBlank() || value.equalsIgnoreCase("all")) ? null : value;
    }
}
