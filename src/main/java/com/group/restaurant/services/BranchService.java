package com.group.restaurant.services;

import com.group.restaurant.dtos.request.branch.BranchAddRequest;
import com.group.restaurant.dtos.request.branch.BranchAdminConfigRequest;
import com.group.restaurant.dtos.request.branch.BranchCopyConfigRequest;
import com.group.restaurant.dtos.request.branch.BranchManagerConfigRequest;
import com.group.restaurant.dtos.request.branch.BranchUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.branch.BranchListResponse;
import com.group.restaurant.dtos.response.branch.BranchResponse;
import com.group.restaurant.enums.BranchStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BranchService {

    BranchResponse createBranch(BranchAddRequest request);

    BranchResponse updateBranch(Long id, BranchUpdateRequest request);

    BranchResponse getBranchById(Long id);

    BranchResponse getBranchByCode(Long restaurantId, String code);

    PageResponse<BranchListResponse> getAllBranches(
            Long restaurantId,
            BranchStatus status,
            Pageable pageable
    );

    List<BranchListResponse> getBranchesByRestaurant(Long restaurantId);

    void changeStatus(Long id, BranchStatus status);

    void deleteBranch(Long id);

    // Quản lý chi nhánh cấu hình
    BranchResponse updateManagerConfig(Long id, BranchManagerConfigRequest request);

    // Quản trị nhà hàng cấu hình (VAT, phí dịch vụ, ngưỡng chiết khấu)
    BranchResponse updateAdminConfig(Long id, BranchAdminConfigRequest request);

    // Sao chép cấu hình từ chi nhánh có sẵn
    BranchResponse copyConfigFromBranch(Long targetBranchId, BranchCopyConfigRequest request);
}
