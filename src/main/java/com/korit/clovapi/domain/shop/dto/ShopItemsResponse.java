package com.korit.clovapi.domain.shop.dto;

import com.korit.clovapi.domain.shop.entity.ShopItem;

import java.util.List;

public record ShopItemsResponse(List<ShopItemResponse> items) {
    public static ShopItemsResponse from(List<ShopItem> items) {
        return new ShopItemsResponse(items.stream().map(ShopItemResponse::from).toList());
    }
}
