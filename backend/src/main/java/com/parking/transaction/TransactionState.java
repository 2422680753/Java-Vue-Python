package com.parking.transaction;

public enum TransactionState {
    PENDING("PENDING", "待处理"),
    PROCESSING("PROCESSING", "处理中"),
    SUCCEEDED("SUCCEEDED", "成功"),
    FAILED("FAILED", "失败"),
    TO_BE_CONFIRMED("TO_BE_CONFIRMED", "待确认"),
    TO_BE_CANCELLED("TO_BE_CANCELLED", "待取消"),
    CONFIRMED("CONFIRMED", "已确认"),
    CANCELLED("CANCELLED", "已取消"),
    COMPENSATING("COMPENSATING", "补偿中"),
    COMPENSATED("COMPENSATED", "已补偿"),
    RETRYING("RETRYING", "重试中"),
    TIMEOUT("TIMEOUT", "超时"),
    UNKNOWN("UNKNOWN", "未知");

    private final String code;
    private final String description;

    TransactionState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFinalState() {
        return this == SUCCEEDED || this == FAILED || this == CONFIRMED || 
               this == CANCELLED || this == COMPENSATED || this == TIMEOUT;
    }

    public boolean canRetry() {
        return this == PENDING || this == FAILED || this == TIMEOUT || this == UNKNOWN;
    }

    public boolean canCompensate() {
        return this == PROCESSING || this == TO_BE_CANCELLED || this == FAILED;
    }
}
