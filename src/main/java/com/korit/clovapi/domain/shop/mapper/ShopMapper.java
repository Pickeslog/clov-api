package com.korit.clovapi.domain.shop.mapper;

import com.korit.clovapi.domain.shop.entity.ShopItem;
import com.korit.clovapi.domain.shop.entity.UserWallet;
import com.korit.clovapi.domain.shop.entity.WalletTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ShopMapper {

    /** category/rarity가 null이면 필터 없이 전체. owned는 이 userId 기준 JOIN 파생값. */
    List<ShopItem> findCatalog(@Param("userId") long userId, @Param("category") String category,
                                @Param("rarity") String rarity);

    List<ShopItem> findInventory(@Param("userId") long userId);

    Optional<ShopItem> findById(@Param("itemId") long itemId);

    /** 구매 트랜잭션 중 잔액 확정을 위해 잠그고 읽는다. */
    Optional<UserWallet> findWalletForUpdate(@Param("userId") long userId);

    Optional<UserWallet> findWallet(@Param("userId") long userId);

    /** INSERT IGNORE라 경합 시 한쪽만 실제로 삽입된다 — 반환값(영향받은 행 수)으로 그걸 구분한다. */
    int insertWallet(@Param("userId") long userId, @Param("balance") long balance);

    void updateBalance(@Param("userId") long userId, @Param("balance") long balance);

    boolean existsInInventory(@Param("userId") long userId, @Param("itemId") long itemId);

    void insertInventory(@Param("userId") long userId, @Param("itemId") long itemId, @Param("paidPrice") long paidPrice);

    void insertTransaction(WalletTransaction transaction);
}
