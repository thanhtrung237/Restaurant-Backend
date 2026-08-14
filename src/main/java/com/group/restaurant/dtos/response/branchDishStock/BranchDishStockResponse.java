package com.group.restaurant.dtos.response.branchDishStock;

import com.group.restaurant.enums.StockStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class BranchDishStockResponse {

    private Long id;
    private Long branchId;
    private String branchName;
    private Long dishId;
    private String dishName;
    private StockStatus status;
    private LocalDateTime modifiedAt;
}
