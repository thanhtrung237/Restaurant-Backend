package com.group.restaurant.dtos.request.dishOptionGroup;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DishOptionGroupAddRequest {

    @NotNull(message = "Dish ID không được để trống")
    private Long dishId;

    @NotNull(message = "Option group ID không được để trống")
    private Long optionGroupId;
}
