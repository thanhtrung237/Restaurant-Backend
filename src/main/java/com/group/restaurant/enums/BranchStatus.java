package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum BranchStatus implements BaseEnum<BranchStatus> {
    INACTIVE(0),
    ACTIVE(1);

    private final int value;

    BranchStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<BranchStatus> {
        public ConverterImpl() {
            super(BranchStatus.class);
        }
    }
}
