package com.group.restaurant.services;

import com.group.restaurant.dtos.response.branch.BranchMenuItemResponse;
import java.util.List;

public interface BranchMenuService {

    List<BranchMenuItemResponse> getMenuForCustomer(Long branchId);

    List<BranchMenuItemResponse> getMenuOverviewForAdmin(Long branchId);
}
