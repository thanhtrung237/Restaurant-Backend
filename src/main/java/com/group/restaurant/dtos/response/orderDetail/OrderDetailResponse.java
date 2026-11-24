package com.group.restaurant.dtos.response.orderDetail;

import com.group.restaurant.enums.CancelReason;
import com.group.restaurant.enums.OrderDetailStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OrderDetailResponse {

    private Long id;
    private Long orderId;
    private Long dishId;
    private String dishName;
    private Long promotionId;
    private String promotionName;
    private Integer quantity;
    private Integer servedQuantity;
    private BigDecimal priceAtOrderTime;
    private BigDecimal promotionDiscountAmount;
    private BigDecimal weightKg;
    private Boolean isTakeAway;
    private String note;
    private OrderDetailStatus status;
    private BigDecimal price;
    // Danh sách options đã chọn (size, topping, etc.)
    private List<OrderDetailOptionResponse> options;
    // Thông tin hủy sau bếp
    private CancelReason cancelReason;
    private Boolean isBillable; // true = vẫn tính tiền dù đã hủy
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
