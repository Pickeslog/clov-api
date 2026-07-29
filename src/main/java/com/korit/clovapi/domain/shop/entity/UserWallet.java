package com.korit.clovapi.domain.shop.entity;

public class UserWallet {

    private long userId;
    private long balance;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }
}
