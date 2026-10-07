package com.group.restaurant.enums;

import jakarta.persistence.Converter;

/**

 * Thuộc cấp CHI NHÁNH, không phải cấp nhà hàng.
 */
public enum StockStatus implements BaseEnum<StockStatus> {

    OUT_OF_STOCK(0), // Hết hàng 
    AVAILABLE(1);    // Còn hàng 

    private final int value;

    StockStatus(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Converter(autoApply = true)
    public static class ConverterImpl extends AbstractEnumConverter<StockStatus> {
        public ConverterImpl() {
            super(StockStatus.class);
        }
    }
}
