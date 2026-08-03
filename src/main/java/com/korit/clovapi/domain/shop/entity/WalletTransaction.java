package com.korit.clovapi.domain.shop.entity;

public class WalletTransaction {

    public static final String REASON_SIGNUP_GRANT = "SIGNUP_GRANT";
    public static final String REASON_PURCHASE = "PURCHASE";
    public static final String REASON_EARN_MASCOT = "EARN_MASCOT";
    public static final String REASON_EARN_MEMORY = "EARN_MEMORY";

    private long userId;
    private String reason;
    private long amount;
    private long balanceAfter;
    private Long referenceId;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(long balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }
}
