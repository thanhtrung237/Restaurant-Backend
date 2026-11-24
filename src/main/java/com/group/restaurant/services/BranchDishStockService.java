package com.group.restaurant.services;

import com.group.restaurant.dtos.response.branchDishStock.BranchDishStockResponse;
import java.util.List;

public interface BranchDishStockService {

    // Báo hết mở lại 1 món tại chi nhánh (toggle)
    BranchDishStockResponse toggleStock(Long branchId, Long dishId);

    // Xem tất cả món đang HẾT tại chi nhánh
    List<BranchDishStockResponse> getOutOfStockByBranch(Long branchId);

    // Xem tất cả stock của chi nhánh (cả còn lẫn hết)
    List<BranchDishStockResponse> getAllStockByBranch(Long branchId);

    /// Mở bán lại hàng loạt cho 1 chi nhánh
    int restoreAllStock(Long branchId);

    //  Reset stock cho chi nhánh (gọi bởi scheduled job)
    int resetStockForBranch(Long branchId);

    // Kiểm tra 1 món có còn hàng tại chi nhánh không (dùng nội bộ)
    boolean isAvailable(Long branchId, Long dishId);
}
