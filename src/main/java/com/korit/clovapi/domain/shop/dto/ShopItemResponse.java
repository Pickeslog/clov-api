package com.korit.clovapi.domain.shop.dto;

import com.korit.clovapi.domain.shop.entity.ShopItem;

public record ShopItemResponse(
        String id,
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
