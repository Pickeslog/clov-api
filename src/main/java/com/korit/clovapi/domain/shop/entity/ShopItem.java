package com.korit.clovapi.domain.shop.entity;

import java.time.LocalDateTime;

public class ShopItem {

    private long id;
    private String name;
    private String description;
    private String category;
    private String rarity;
    private long price;
    private int discountRate;
    private String imageUrl;
    private boolean purchasable;
    private boolean owned;
    private LocalDateTime createdAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRarity() {
        return rarity;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public int getDiscountRate() {
        return discountRate;
    }

    public void setDiscountRate(int discountRate) {
        this.discountRate = discountRate;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isPurchasable() {
        return purchasable;
    }

    public void setPurchasable(boolean purchasable) {
        this.purchasable = purchasable;
    }

    /** 조회 시 JOIN으로 채워지는 파생 값 — 이 사용자의 보유 여부. */
    public boolean isOwned() {
        return owned;
    }

    public void setOwned(boolean owned) {
        this.owned = owned;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** 최종가 = 정가 - 할인율. 프론트에는 항상 이 값을 계산해서 내려준다(계약: 청구액은 서버가 계산). */
    public long finalPrice() {
        return price - (price * discountRate / 100);
    }
}
