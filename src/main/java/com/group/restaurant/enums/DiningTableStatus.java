package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum DiningTableStatus implements BaseEnum<DiningTableStatus> {
    INACTIVE(0), // NGỪNG HOẠT ĐỘNG
    AVAILABLE(1), // CÓ SẴN
    OCCUPIED(2), // ĐÃ ĐƯỢC SỬ DỤNG
    RESERVED(3); // ĐÃ ĐƯỢC ĐẶT

    private final int value;

    DiningTableStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<DiningTableStatus> {
        public ConverterImpl() {
            super(DiningTableStatus.class);
        }
    }
}
