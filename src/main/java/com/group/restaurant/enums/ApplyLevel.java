package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum ApplyLevel implements BaseEnum<ApplyLevel> {
    ITEM(1),
    INVOICE(2);

    private final int value;

    ApplyLevel(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<ApplyLevel> {
        public ConverterImpl() {
            super(ApplyLevel.class);
        }
    }
}
