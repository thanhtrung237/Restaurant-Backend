package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum PaymentMethod implements BaseEnum<PaymentMethod> {
    CASH(1),
    CARD(2),
    BANK_TRANSFER(3),
    E_WALLET(4);

    private final int value;

    PaymentMethod(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<PaymentMethod> {
        public ConverterImpl() {
            super(PaymentMethod.class);
        }
    }
}
