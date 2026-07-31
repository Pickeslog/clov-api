package com.korit.clovapi.domain.shop.entity;

import java.time.LocalDateTime;

public class ShopItem {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String CATEGORY_COSTUME = "COSTUME";

    private long id;
    private String code;
    private String name;
    private String description;
    private String category;
    private String rarity;
    private long price;
    private int discountRate;
    private String imageUrl;
    private String status;
    private int sortOrder;
    private boolean owned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isPurchasable() {
        return STATUS_ACTIVE.equals(status);
    }

    /** 최종가 = 정가 - 할인율. 프론트에는 항상 이 값을 계산해서 내려준다(청구액은 서버가 계산). */
    public long finalPrice() {
        return price - (price * discountRate / 100);
    }
}
