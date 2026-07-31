package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum TableSessionStatus implements BaseEnum<TableSessionStatus> {
    CLOSED(0),
    OPEN(1);

    private final int value;

    TableSessionStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<TableSessionStatus> {
        public ConverterImpl() {
            super(TableSessionStatus.class);
        }
    }
}
