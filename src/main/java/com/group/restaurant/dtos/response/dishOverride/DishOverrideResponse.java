package com.group.restaurant.dtos.response.dishOverride;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DishOverrideResponse {

    private Long id;
    private Long branchId;
    private String branchName;
    private Long dishId;
    private String dishName;
    private BigDecimal overridePrice;
    private Boolean isHidden;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
