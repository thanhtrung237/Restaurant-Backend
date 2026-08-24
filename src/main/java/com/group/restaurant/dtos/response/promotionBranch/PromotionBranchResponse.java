package com.group.restaurant.dtos.response.promotionBranch;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PromotionBranchResponse {

    private Long id;
    private Long branchId;
    private String branchName;
    private Long promotionId;
    private String promotionName;
    private LocalDateTime createdAt;
}
