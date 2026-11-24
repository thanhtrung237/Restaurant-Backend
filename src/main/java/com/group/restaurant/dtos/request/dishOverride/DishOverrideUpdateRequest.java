package com.group.restaurant.dtos.request.dishOverride;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DishOverrideUpdateRequest {

    private BigDecimal overridePrice;
    private boolean clearOverridePrice;

    private Boolean isHidden;
}
