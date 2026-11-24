package com.group.restaurant.services;

import com.group.restaurant.dtos.request.promotionBranch.PromotionBranchAddRequest;
import com.group.restaurant.dtos.response.promotionBranch.PromotionBranchResponse;
import java.util.List;

public interface PromotionBranchService {

    PromotionBranchResponse assignPromotionToBranch(PromotionBranchAddRequest request);

    List<PromotionBranchResponse> getByBranch(Long branchId);

    List<PromotionBranchResponse> getByPromotion(Long promotionId);

    void removePromotionFromBranch(Long id);
}
