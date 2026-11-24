package com.group.restaurant.dtos.response.branchDishStock;

import com.group.restaurant.enums.StockStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

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
