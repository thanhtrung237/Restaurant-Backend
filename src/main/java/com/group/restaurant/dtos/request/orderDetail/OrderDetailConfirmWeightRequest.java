package com.group.restaurant.dtos.request.orderDetail;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
  Phục vụ nhập số cân thật cho món bán theo cân: CHỜ CÂN → ĐANG CHẾ BIẾN
 */
@Getter
@Setter
public class OrderDetailConfirmWeightRequest {

    @NotNull(message = "Số cân không được để trống")
    @DecimalMin(value = "0.001", message = "Số cân phải > 0")
    private BigDecimal weightKg;
}
