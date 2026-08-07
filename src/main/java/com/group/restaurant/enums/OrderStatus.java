package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum OrderStatus implements BaseEnum<OrderStatus> {
    PENDING(0),
    CONFIRMED(1),
    COMPLETED(2),
    CANCELLED(3);

    private final int value;

    OrderStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<OrderStatus> {
        public ConverterImpl() {
            super(OrderStatus.class);
        }
    }
}
