package com.group.restaurant.dtos.request.orderDetail;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class OrderDetailAddRequest {

    @NotNull(message = "Menu ID (món ăn) không được để trống")
    private Long menuId;

    private Long promotionId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng phải >= 1")
    private Integer quantity;

    @DecimalMin(value = "0.001", message = "Trọng lượng phải > 0")
    private BigDecimal weightKg;

    private Boolean isTakeAway;

    private String note;
}
