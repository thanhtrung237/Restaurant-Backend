package com.group.restaurant.services;

import com.group.restaurant.dtos.request.promotion.PromotionAddRequest;
import com.group.restaurant.dtos.request.promotion.PromotionUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.promotion.PromotionResponse;
import com.group.restaurant.enums.PromotionStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PromotionService {

    PromotionResponse createPromotion(PromotionAddRequest request);

    PromotionResponse updatePromotion(Long id, PromotionUpdateRequest request);

    PromotionResponse getPromotionById(Long id);

    PageResponse<PromotionResponse> getPromotionsByRestaurant(Long restaurantId, PromotionStatus status, Pageable pageable);

    void changeStatus(Long id, PromotionStatus status);

    void deletePromotion(Long id);

    List<PromotionResponse> getActivePromotionsByBranch(Long branchId);

    PromotionResponse getPromotionByBranchAndCode(Long branchId, String code);
}
