package com.group.restaurant.enums;

public enum AnomalyType {
    SURGE_CANCELLATION("Tỷ lệ hủy món tăng đột biến"),
    SUSPICIOUS_DISCOUNT("Giảm giá thủ công bất thường"),
    REPEATED_TABLE_CANCELLATIONS("Bàn bị hủy món nhiều lần"),
    UNUSUAL_VOID_RATE("Đơn hàng bị void bất thường");

    private final String description;

    AnomalyType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
