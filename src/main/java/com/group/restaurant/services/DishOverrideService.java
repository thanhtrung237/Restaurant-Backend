package com.group.restaurant.services;

import com.group.restaurant.dtos.request.dishOverride.DishOverrideAddRequest;
import com.group.restaurant.dtos.request.dishOverride.DishOverrideUpdateRequest;
import com.group.restaurant.dtos.response.dishOverride.DishOverrideResponse;

import java.util.List;

public interface DishOverrideService {

    DishOverrideResponse createDishOverride(DishOverrideAddRequest request);

    DishOverrideResponse updateDishOverride(Long id, DishOverrideUpdateRequest request);

    DishOverrideResponse getDishOverrideById(Long id);

    List<DishOverrideResponse> getDishOverridesByBranch(Long branchId);

    List<DishOverrideResponse> getDishOverridesByDish(Long dishId);

    void deleteDishOverride(Long id);
}
