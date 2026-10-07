package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum CategoryStatus implements BaseEnum<CategoryStatus> {
    INACTIVE(0),
    ACTIVE(1);

    private final int value;

    CategoryStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<CategoryStatus> {
        public ConverterImpl() {
            super(CategoryStatus.class);
        }
    }
}
