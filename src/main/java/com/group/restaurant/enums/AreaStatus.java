package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum AreaStatus implements BaseEnum<AreaStatus> {
    INACTIVE(0),
    ACTIVE(1);

    private final int value;

    AreaStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<AreaStatus> {
        public ConverterImpl() {
            super(AreaStatus.class);
        }
    }
}
