package com.parking.transaction;

public enum TransactionType {
    PAYMENT_CREATE("PAYMENT_CREATE", "创建支付订单"),
    PAYMENT_PROCESS("PAYMENT_PROCESS", "处理支付"),
    PAYMENT_CONFIRM("PAYMENT_CONFIRM", "确认支付"),
    PAYMENT_CANCEL("PAYMENT_CANCEL", "取消支付"),
    PAYMENT_REFUND("PAYMENT_REFUND", "退款"),
    VEHICLE_ENTRY("VEHICLE_ENTRY", "车辆入场"),
    VEHICLE_EXIT("VEHICLE_EXIT", "车辆出场"),
    SPOT_OCCUPY("SPOT_OCCUPY", "车位占用"),
    SPOT_RELEASE("SPOT_RELEASE", "车位释放"),
    BALANCE_DEDUCT("BALANCE_DEDUCT", "余额扣款"),
    BALANCE_REFUND("BALANCE_REFUND", "余额退款"),
    ORDER_COMPLETE("ORDER_COMPLETE", "订单完成"),
    ORDER_CANCEL("ORDER_CANCEL", "订单取消");

    private final String code;
    private final String description;

    TransactionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public TransactionType getCompensateType() {
        switch (this) {
            case PAYMENT_CREATE:
                return PAYMENT_CANCEL;
            case PAYMENT_PROCESS:
                return PAYMENT_REFUND;
            case PAYMENT_CONFIRM:
                return PAYMENT_REFUND;
            case VEHICLE_ENTRY:
                return VEHICLE_EXIT;
            case SPOT_OCCUPY:
                return SPOT_RELEASE;
            case BALANCE_DEDUCT:
                return BALANCE_REFUND;
            case ORDER_COMPLETE:
                return ORDER_CANCEL;
            default:
                return null;
        }
    }

    public boolean requiresCompensation() {
        return this == PAYMENT_CREATE || this == PAYMENT_PROCESS || 
               this == PAYMENT_CONFIRM || this == VEHICLE_ENTRY ||
               this == SPOT_OCCUPY || this == BALANCE_DEDUCT ||
               this == ORDER_COMPLETE;
    }
}
