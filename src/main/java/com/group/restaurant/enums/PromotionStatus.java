package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum PromotionStatus implements BaseEnum<PromotionStatus> {
    INACTIVE(0),
    ACTIVE(1);

    private final int value;

    PromotionStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<PromotionStatus> {
        public ConverterImpl() {
            super(PromotionStatus.class);
        }
    }
}
