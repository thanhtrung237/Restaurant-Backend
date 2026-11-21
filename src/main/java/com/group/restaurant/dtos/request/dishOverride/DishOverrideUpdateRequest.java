package com.group.restaurant.dtos.request.dishOverride;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DishOverrideUpdateRequest {

    private BigDecimal overridePrice;
    private boolean clearOverridePrice;

    private Boolean isHidden;
}
