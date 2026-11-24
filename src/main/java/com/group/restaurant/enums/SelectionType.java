package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum SelectionType implements BaseEnum<SelectionType> {
    REQUIRED(0), // Bắt buộc chọn
    OPTIONAL(1); // Không bắt buộc

    private final int value;

    SelectionType(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<SelectionType> {
        public ConverterImpl() {
            super(SelectionType.class);
        }
    }
}
