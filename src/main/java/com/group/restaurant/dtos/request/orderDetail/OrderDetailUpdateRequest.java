package com.group.restaurant.dtos.request.orderDetail;

import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderDetailUpdateRequest {

    @Min(value = 1, message = "Số lượng phải >= 1")
    private Integer quantity;

    private BigDecimal weightKg;

    private Boolean isTakeAway;

    private String note;
}
