package com.group.restaurant.dtos.response.orderDetail;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO cho OrderDetailOption - tùy chọn món ăn đã chọn (size, topping, etc.)
 */
@Getter
@Setter
@Builder
public class OrderDetailOptionResponse {
    private Long id;
    private Long optionItemId;
    private String optionItemName;
    private BigDecimal priceSnapshot; // Giá tại thời điểm gọi món
}
