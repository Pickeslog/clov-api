package com.korit.clovapi.domain.shop.dto;

import com.korit.clovapi.domain.shop.entity.ShopItem;

/**
 * 상점 아이템 응답.
 *
 * ★ code 는 화면이 특정 아이템을 지목해야 할 때 쓰는 안정적인 키다(id 는 환경마다 다른
 *   auto-increment PK 라 프론트에 상수로 박을 수 없다). 배경(BACKGROUND) 상품이 이걸 쓴다 —
 *   사용자설정 > 바탕화면이 보유한 배경만 고를 수 있게 거르는데, 그 대조를 code 로 한다.
 *   imageUrl 로 대조하면 썸네일 경로를 바꾸는 순간 이미 산 사람의 소유가 풀린다.
 */
public record ShopItemResponse(
        String id,
        String code,
        String name,
        String description,
        String category,
        String rarity,
        long price,
        int discountRate,
        long finalPrice,
        String imageUrl,
        boolean owned
) {
    public static ShopItemResponse from(ShopItem item) {
        return new ShopItemResponse(
                String.valueOf(item.getId()),
                item.getCode(),
                item.getName(),
                item.getDescription(),
                item.getCategory(),
                item.getRarity(),
                item.getPrice(),
                item.getDiscountRate(),
                item.finalPrice(),
                item.getImageUrl(),
                item.isOwned()
        );
    }
}
