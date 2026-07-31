package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum OrderDetailStatus implements BaseEnum<OrderDetailStatus> {
    PENDING(0),
    PREPARING(1),
    SERVED(2),
    CANCELLED(3);

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
