package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum TableSessionStatus implements BaseEnum<TableSessionStatus> {
    WAITING_FOR_OPEN(0), // CHỜ MỞ BÀN
    SERVING(1), // ĐANG PHỤC VỤ
    WAITING_FOR_PAY(2), // CHỜ THANH TOÁN
    CLOSED(3), // ĐÃ ĐÓNG
    CANCELLED(4), // ĐÃ HỦY
    RESERVED(5), // ĐẶT TRƯỚC
    MERGED(6); // ĐÃ GỘP (BR-28)

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
