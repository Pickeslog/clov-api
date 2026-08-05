package com.korit.clovapi.domain.shop.entity;

public class WalletTransaction {

    public static final String REASON_SIGNUP_GRANT = "SIGNUP_GRANT";
    public static final String REASON_PURCHASE = "PURCHASE";
    /**
     * ★ 획득 사유는 반드시 "EARN_" 으로 시작한다(계약 §15-4). 하루 총 상한 합산이
     * `reason LIKE 'EARN\_%'` 접두사 매칭이라, 접두사를 안 지키면 그 사유가 조용히
     * 캡 밖으로 빠져 무한히 쌓인다. 반대로 운영 지급(ADMIN_GRANT)처럼 캡에 잡히면
     * 안 되는 사유는 이 접두사를 쓰지 않는다 — 받은 날 정상 획득이 통째로 막힌다.
     */
    public static final String REASON_EARN_MASCOT = "EARN_MASCOT";
    public static final String REASON_EARN_MEMORY = "EARN_MEMORY";
    /**
     * 자유 추억(plan_id NULL) 등록 지급. EARN_MEMORY 와 합치지 않는 이유는 하루 횟수 캡
     * 때문이다 — 횟수는 사유별로 세므로(countReasonToday) 합치면 자유 추억의 10회 캡이
     * 약속 연결 추억까지 묶어버린다. 하루에 약속을 열 바퀴 완주해도 열 번까지만 받게 된다.
     */
    public static final String REASON_EARN_MEMORY_FREE = "EARN_MEMORY_FREE";

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
