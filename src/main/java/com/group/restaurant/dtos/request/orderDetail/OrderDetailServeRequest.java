package com.group.restaurant.dtos.request.orderDetail;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record OrderDetailServeRequest(
        @NotNull @Positive Integer quantity,
        @NotNull @PositiveOrZero Integer expectedServedQuantity) {}
