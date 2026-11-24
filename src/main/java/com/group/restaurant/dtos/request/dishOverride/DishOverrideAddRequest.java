package com.group.restaurant.dtos.request.dishOverride;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DishOverrideAddRequest {

    @NotNull(message = "Branch ID không được để trống")
    private Long branchId;

    @NotNull(message = "Dish ID không được để trống")
    private Long dishId;

    private BigDecimal overridePrice;

    private Boolean isHidden;
}
