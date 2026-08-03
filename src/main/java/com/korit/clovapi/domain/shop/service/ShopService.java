package com.korit.clovapi.domain.shop.service;

import com.korit.clovapi.domain.shop.dto.PurchaseResponse;
import com.korit.clovapi.domain.shop.dto.ShopItemResponse;
import com.korit.clovapi.domain.shop.dto.ShopItemsResponse;
import com.korit.clovapi.domain.shop.dto.WalletResponse;
import com.korit.clovapi.domain.shop.entity.ShopItem;
import com.korit.clovapi.domain.shop.entity.UserWallet;
import com.korit.clovapi.domain.shop.entity.WalletTransaction;
import com.korit.clovapi.domain.shop.mapper.ShopMapper;
import com.korit.clovapi.domain.user.mapper.UserPreferenceMapper;
import com.korit.clovapi.global.dto.EquippedItemResponse;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import com.korit.clovapi.global.time.ClovTime;
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

    /**
     * 지갑 최초 생성 시 지급하는 시작 골드 (계약 §15-1 (2), 리더 확정 2026-07-30).
     *
     * ⚠️ 카탈로그 총액에 가깝게 올리지 말 것. 이전 값 20,000은 설계값이 아니라 개발 DB의
     * 테스트 원장 데이터에 코드를 맞춘 숫자였는데, 카탈로그 총액(42,260)의 절반이라
     * 가입 즉시 절반을 살 수 있었다 — 그러면 §15-4의 획득 규칙(하루 500)이 무의미해진다.
     * 1,000은 가장 싼 코스튬(600G) 하나를 바로 사고 두 번째는 못 사는 금액이다.
     */
    static final long SIGNUP_GRANT_AMOUNT = 1000;

    /** 골드 획득(EARN_*) 일일 총 상한(계약 §15-4) — 사유와 무관하게 유저 단위로 하루 누적을 센다. */
    private static final long DAILY_EARN_CAP = 500;

    private final ShopMapper shopMapper;
    private final UserPreferenceMapper preferenceMapper;

    public ShopService(ShopMapper shopMapper, UserPreferenceMapper preferenceMapper) {
        this.shopMapper = shopMapper;
        this.preferenceMapper = preferenceMapper;
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

    /** 오늘 범위: COSTUME만 장착 가능(마스코트 이미지 자체를 교체하는 용도). SKIN/EVENT는 범위 밖. */
    @Transactional
    public EquippedItemResponse equip(long userId, long itemId) {
        ShopItem item = shopMapper.findById(itemId)
                .orElseThrow(() -> new DomainException(ErrorCode.SHOP_ITEM_NOT_FOUND));
        if (!shopMapper.existsInInventory(userId, itemId)) {
            throw new DomainException(ErrorCode.ITEM_NOT_OWNED);
        }
        if (!ShopItem.CATEGORY_COSTUME.equals(item.getCategory())) {
            throw new DomainException(ErrorCode.ITEM_NOT_EQUIPPABLE);
        }

        ensurePreferencesExist(userId);
        preferenceMapper.updateEquippedItem(userId, itemId);
        return new EquippedItemResponse(String.valueOf(item.getId()), item.getName(), item.getImageUrl());
    }

    @Transactional
    public void unequip(long userId) {
        ensurePreferencesExist(userId);
        preferenceMapper.updateEquippedItem(userId, null);
    }

    /**
     * 골드 적립 공통 진입점(마스코트 교감·추억 등록 등 지급 사유가 늘어날 때 공용으로 쓴다).
     * 사유별 횟수 제한(예: 마스코트 하루 3회)은 호출부가 직접 확인하고 부른다 — 여기선
     * 사유와 무관한 하루 총 상한(§15-4, 500골드)만 막는다. 상한 도달 시 조용히 지급하지
     * 않는다(예외를 던지지 않는다) — XP는 이미 별도로 지급됐고, 골드만 안 오르면 된다.
     * 반환값은 실제로 지급된 골드(0 또는 amount) — 호출부가 응답 DTO·말풍선에 실지급액을
     * 그대로 실어 보내야, 캡에 걸려 조용히 0이 지급된 순간에도 화면이 거짓말을 하지 않는다.
     */
    @Transactional
    public long earnGold(long userId, String reason, long amount, Long referenceId) {
        long earnedToday = shopMapper.sumEarnedToday(userId, ClovTime.startOfTodayUtc());
        if (earnedToday >= DAILY_EARN_CAP) {
            return 0;
        }

        getOrCreateWallet(userId);
        UserWallet wallet = shopMapper.findWalletForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("wallet must exist after getOrCreateWallet"));

        long newBalance = wallet.getBalance() + amount;
        shopMapper.updateBalance(userId, newBalance);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserId(userId);
        transaction.setReason(reason);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(newBalance);
        transaction.setReferenceId(referenceId);
        shopMapper.insertTransaction(transaction);
        return amount;
    }

    /** 특정 사유의 오늘(유저 단위) 지급 횟수 — 호출부가 자기 사유의 하루 횟수 캡을 판정할 때 쓴다. */
    @Transactional(readOnly = true)
    public int countEarnedToday(long userId, String reason) {
        return shopMapper.countReasonToday(userId, reason, ClovTime.startOfTodayUtc());
    }

    /**
     * user_preferences 행은 preferences 조회/수정 API를 한 번도 안 거친 사용자에겐 아직 없다.
     * updateEquippedItem은 UPDATE라 행이 없으면 0건 갱신되고도 조용히 성공한 것처럼 보인다
     * (실제로 겪은 버그 — 신규 유저가 Settings를 안 열고 바로 장착하면 응답은 200인데 반영 안 됨).
     */
    private void ensurePreferencesExist(long userId) {
        if (preferenceMapper.findByUserId(userId).isEmpty()) {
            preferenceMapper.insertDefault(userId);
        }
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
