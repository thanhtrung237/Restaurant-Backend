package com.group.restaurant.enums;

import jakarta.persistence.Converter;

public enum InvoiceStatus implements BaseEnum<InvoiceStatus> {
    PENDING(0), // Hóa đơn nội bộ đã tạo, chưa thu tiền
    PAID(1), // Đã thu đủ tiền, chưa phát hành HĐĐT
    ISSUED(4), // Đã phát hành hóa đơn điện tử thành công lên cơ quan thuế
    CANCELLED(2), // Hủy
    REFUNDED(3); // Hoàn tiền

    private final int value;

    InvoiceStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<InvoiceStatus> {
        public ConverterImpl() {
            super(InvoiceStatus.class);
        }
    }
}
