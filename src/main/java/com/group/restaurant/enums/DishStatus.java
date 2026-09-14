package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum DishStatus implements BaseEnum<DishStatus> {
    INACTIVE(0),
    ACTIVE(1);

    private final int value;

    DishStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<DishStatus> {
        public ConverterImpl() {
            super(DishStatus.class);
        }
    }
}
