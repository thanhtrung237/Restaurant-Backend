package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum AccountStatus implements BaseEnum<AccountStatus> {
    INACTIVE(0),
    ACTIVE(1);

    private final int value;

    AccountStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<AccountStatus> {
        public ConverterImpl() {
            super(AccountStatus.class);
        }
    }
}
