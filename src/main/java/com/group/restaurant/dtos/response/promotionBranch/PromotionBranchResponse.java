package com.group.restaurant.dtos.response.promotionBranch;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

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
