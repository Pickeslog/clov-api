package com.korit.clovapi.domain.shop.controller;

import com.korit.clovapi.domain.shop.dto.PurchaseResponse;
import com.korit.clovapi.domain.shop.dto.ShopItemsResponse;
import com.korit.clovapi.domain.shop.dto.WalletResponse;
import com.korit.clovapi.domain.shop.service.ShopService;
import com.korit.clovapi.global.dto.EquippedItemResponse;
import com.korit.clovapi.global.response.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping("/api/v1/shop/items")
    public ApiResponse<ShopItemsResponse> findCatalog(
            Authentication authentication,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String rarity
    ) {
        return ApiResponse.success(shopService.findCatalog(currentUserId(authentication), category, rarity));
    }

    @GetMapping("/api/v1/shop/wallet")
    public ApiResponse<WalletResponse> getWallet(Authentication authentication) {
        return ApiResponse.success(shopService.getWallet(currentUserId(authentication)));
    }

    @GetMapping("/api/v1/shop/inventory")
    public ApiResponse<ShopItemsResponse> findInventory(Authentication authentication) {
        return ApiResponse.success(shopService.findInventory(currentUserId(authentication)));
    }

    @PostMapping("/api/v1/shop/items/{itemId}/purchase")
    public ApiResponse<PurchaseResponse> purchase(Authentication authentication, @PathVariable long itemId) {
        return ApiResponse.success(shopService.purchase(currentUserId(authentication), itemId));
    }

    @PostMapping("/api/v1/shop/items/{itemId}/equip")
    public ApiResponse<EquippedItemResponse> equip(Authentication authentication, @PathVariable long itemId) {
        return ApiResponse.success(shopService.equip(currentUserId(authentication), itemId));
    }

    @DeleteMapping("/api/v1/shop/equipped")
    public ApiResponse<Void> unequip(Authentication authentication) {
        shopService.unequip(currentUserId(authentication));
        return ApiResponse.success(null);
    }

    private long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
