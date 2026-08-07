package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum InvoiceStatus implements BaseEnum<InvoiceStatus> {
    PENDING(0),
    PAID(1),
    CANCELLED(2),
    REFUNDED(3);

    private final int value;

    InvoiceStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<InvoiceStatus> {
        public ConverterImpl() {
            super(InvoiceStatus.class);
        }
    }
}
