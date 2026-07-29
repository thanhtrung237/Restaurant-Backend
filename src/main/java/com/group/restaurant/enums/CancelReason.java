package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum CancelReason implements BaseEnum<CancelReason> {
    RESTAURANT_ERROR(0),        // Lỗi nhà hàng — không tính tiền
    CUSTOMER_CHANGED_MIND(1);   // Khách đổi ý — vẫn tính tiền

    private final int value;

    CancelReason(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<CancelReason> {
        public ConverterImpl() {
            super(CancelReason.class);
        }
    }
}
