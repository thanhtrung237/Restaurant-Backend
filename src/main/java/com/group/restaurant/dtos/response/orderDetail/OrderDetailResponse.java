package com.group.restaurant.dtos.response.orderDetail;

import com.group.restaurant.enums.OrderDetailStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class OrderDetailResponse {

    private Long id;
    private Long orderId;
    private Long menuId;
    private String menuName;
    private Long promotionId;
    private String promotionName;
    private Integer quantity;
    private BigDecimal priceAtOrderTime;
    private BigDecimal promotionDiscountAmount;
    private BigDecimal weightKg;
    private Boolean isTakeAway;
    private String note;
    private OrderDetailStatus status;
    private BigDecimal price;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
