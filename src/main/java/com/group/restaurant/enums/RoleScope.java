package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum RoleScope implements BaseEnum<RoleScope> {
    PLATFORM(1),
    RESTAURANT(2),
    BRANCH(3);

    private final int value;

    RoleScope(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<RoleScope> {
        public ConverterImpl() {
            super(RoleScope.class);
        }
    }
}
