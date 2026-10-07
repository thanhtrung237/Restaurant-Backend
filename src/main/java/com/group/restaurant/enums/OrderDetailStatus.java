package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum OrderDetailStatus implements BaseEnum<OrderDetailStatus> {
    PENDING(0),          // CHỜ XÁC NHẬN
    PREPARING(1),        // ĐANG CHẾ BIẾN
    SERVED(2),           // ĐÃ PHỤC VỤ
    CANCELLED(3),        // ĐÃ HỦY
    PENDING_WEIGHT(4);   // CHỜ CÂN

    private final int value;

    OrderDetailStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<OrderDetailStatus> {
        public ConverterImpl() {
            super(OrderDetailStatus.class);
        }
    }
}
