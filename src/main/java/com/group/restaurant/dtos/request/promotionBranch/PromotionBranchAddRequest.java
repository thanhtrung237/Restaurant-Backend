package com.group.restaurant.dtos.request.promotionBranch;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PromotionBranchAddRequest {

    @NotNull(message = "Branch ID không được để trống")
    private Long branchId;

    @NotNull(message = "Promotion ID không được để trống")
    private Long promotionId;
}
