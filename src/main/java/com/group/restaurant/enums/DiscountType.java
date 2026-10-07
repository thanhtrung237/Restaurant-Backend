package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum DiscountType implements BaseEnum<DiscountType> {
    PERCENTAGE(1),
    FIXED_AMOUNT(2);

    private final int value;

    DiscountType(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<DiscountType> {
        public ConverterImpl() {
            super(DiscountType.class);
        }
    }
}
