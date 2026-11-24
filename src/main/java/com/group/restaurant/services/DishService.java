package com.group.restaurant.services;

import com.group.restaurant.dtos.request.dish.DishAddRequest;
import com.group.restaurant.dtos.request.dish.DishUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.dish.DishResponse;
import com.group.restaurant.enums.DishStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface DishService {

    DishResponse createDish(DishAddRequest request);

    DishResponse updateDish(Long id, DishUpdateRequest request);

    DishResponse getDishById(Long id);

    PageResponse<DishResponse> getDishesByRestaurant(
            Long restaurantId, DishStatus status, Pageable pageable);

    // Lấy món riêng của một chi nhánh (FR-MNU-04)
    List<DishResponse> getDishesByBranch(Long branchId);

    List<DishResponse> getDishesByCategory(Long categoryId);

    void changeStatus(Long id, DishStatus status);

    // Ngừng bán món gốc — trả về số chi nhánh bị ảnh hưởng (FR-MNU-04)
    /**
     * QĐ-05 — số chi nhánh ĐANG BÁN món này, dùng cho cảnh báo trước khi ngừng bán / xoá.
     * Không phải "số chi nhánh đang đè giá" như tên hàm cũ.
     */
    int countBranchesSellingDish(Long id);

    void deleteDish(Long id);
}
