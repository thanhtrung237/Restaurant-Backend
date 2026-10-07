package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum RestaurantStatus implements BaseEnum<RestaurantStatus> {
    INACTIVE(0),
    ACTIVE(1);

    private final int value;

    RestaurantStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<RestaurantStatus> {
        public ConverterImpl() {
            super(RestaurantStatus.class);
        }
    }
}
