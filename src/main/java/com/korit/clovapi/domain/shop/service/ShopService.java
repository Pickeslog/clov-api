package com.korit.clovapi.domain.shop.service;

import com.korit.clovapi.domain.shop.dto.PurchaseResponse;
import com.korit.clovapi.domain.shop.dto.ShopItemResponse;
import com.korit.clovapi.domain.shop.dto.ShopItemsResponse;
import com.korit.clovapi.domain.shop.dto.ShopTransactionsResponse;
import com.korit.clovapi.domain.shop.dto.WalletResponse;
import com.korit.clovapi.domain.shop.dto.WalletTransactionResponse;
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

import java.util.List;

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
     * 테스트 원장 데이터에 코드를 맞춘 숫자였는데, 카탈로그 총액의 절반이라 가입 즉시
     * 절반을 살 수 있었다 — 그러면 §15-4의 획득 규칙이 무의미해진다.
     * 1,000은 가장 싼 코스튬(900G) 하나를 바로 사고 두 번째는 못 사는 금액이다.
     */
    static final long SIGNUP_GRANT_AMOUNT = 1000;

    /**
     * 골드 획득(EARN_*) 일일 총 상한(계약 §15-4, 리더 확정 2026-08-05) — 사유와 무관하게
     * 유저 단위로 하루 누적을 센다.
     *
     * ⚠️ 경로별 자체 캡의 합(마스코트 2,000 + 자유 추억 2,000 = 4,000)보다 커야 한다.
     * 4,000 이하로 내리면 약속 완주(EARN_MEMORY)가 예산에서 통째로 밀려나, 핵심 흐름
     * "약속 완료 후 추억 작성"에 골드가 닿지 못한다. 값을 내리려면 계약 §15-4의
     * "카탈로그를 먼저 본다"를 따를 것 — active_catalog_total 을 재고, 여러 날에 걸쳐
     * 한자리에 머무를 때만 내린다.
     *
     * ★ EARN_MEMORY 에는 자체 횟수 캡이 없다(약속 개수에 제한이 없어 만들기 → 완료 →
     * 작성을 반복하면 한 바퀴 300골드다). 이 상수가 그 경로의 유일한 방어선이다.
     */
    private static final long DAILY_EARN_CAP = 6000;

    /** 원장 조회(#135) 기본 페이지 크기 — MemoryService.DEFAULT_PAGE_SIZE와 같은 값. */
    private static final int TRANSACTIONS_DEFAULT_PAGE_SIZE = 20;

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
     * 사유별 횟수·내용 제한(마스코트 하루 10회, 자유 추억 하루 10회 + 본문 20자)은 호출부가
     * 직접 확인하고 부른다 — 여기선 사유와 무관한 하루 총 상한(§15-4, 6,000골드)만 막는다.
     * 상한 도달 시 조용히 지급하지 않는다(예외를 던지지 않는다) — XP는 이미 별도로 지급됐고,
     * 골드만 안 오르면 된다.
     * 반환값은 실제로 지급된 골드(0 또는 amount) — 호출부가 응답 DTO·말풍선에 실지급액을
     * 그대로 실어 보내야, 캡에 걸려 조용히 0이 지급된 순간에도 화면이 거짓말을 하지 않는다.
     */
    @Transactional
    public long earnGold(long userId, String reason, long amount, Long referenceId) {
        // ★ 캡 판정은 반드시 행 잠금을 잡은 뒤에 읽는다. 순서를 뒤집으면(sumEarnedToday → if →
        // findWalletForUpdate) 동시 요청 둘이 잠금 밖에서 같은 누적치를 읽고 둘 다 통과해,
        // 상한을 amount 만큼 넘겨 지급된다. 같은 유저의 지급은 이 잠금으로 직렬화된다.
        getOrCreateWallet(userId);
        UserWallet wallet = shopMapper.findWalletForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("wallet must exist after getOrCreateWallet"));

        long earnedToday = shopMapper.sumEarnedToday(userId, ClovTime.startOfTodayUtc());
        if (earnedToday >= DAILY_EARN_CAP) {
            return 0;
        }

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
     * 원장 조회(#135) — 골드가 왜 0인지 화면에서 확인할 방법이 없던 문제. 목록과 함께
     * "오늘 얼마나 벌었나·상한에 얼마나 남았나"를 같이 준다(계약 §15-4).
     *
     * ⚠️ getWallet()과 마찬가지로 지갑을 지연 생성한다. /shop/wallet을 한 번도 안 부른
     * 신규 유저가 이 API부터 먼저 호출하면, 지갑이 없어 SIGNUP_GRANT조차 안 보이는
     * 빈 목록을 받는다 — 이슈가 풀려던 문제("골드가 왜 0인지 모른다")를 그대로
     * 재현하게 되므로 여기서도 반드시 생성해야 한다.
     */
    @Transactional
    public ShopTransactionsResponse getTransactions(long userId, int page, int size) {
        getOrCreateWallet(userId);
        int pageSize = size > 0 ? size : TRANSACTIONS_DEFAULT_PAGE_SIZE;
        int offset = Math.max(page, 0) * pageSize;
        List<WalletTransaction> rows = shopMapper.findTransactions(userId, pageSize, offset);
        long earnedToday = shopMapper.sumEarnedToday(userId, ClovTime.startOfTodayUtc());
        long remaining = Math.max(DAILY_EARN_CAP - earnedToday, 0);
        return new ShopTransactionsResponse(
                rows.stream().map(WalletTransactionResponse::from).toList(),
                earnedToday, DAILY_EARN_CAP, remaining
        );
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
