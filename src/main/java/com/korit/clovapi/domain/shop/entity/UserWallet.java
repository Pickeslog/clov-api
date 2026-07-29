package com.korit.clovapi.domain.shop.entity;

public class UserWallet {

    private long userId;
    private long goldBalance;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getGoldBalance() {
        return goldBalance;
    }

    public void setGoldBalance(long goldBalance) {
        this.goldBalance = goldBalance;
    }
}
