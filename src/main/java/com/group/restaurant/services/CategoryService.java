package com.group.restaurant.services;

import com.group.restaurant.dtos.request.category.CategoryAddRequest;
import com.group.restaurant.dtos.request.category.CategoryUpdateRequest;
import com.group.restaurant.dtos.response.category.CategoryResponse;
import com.group.restaurant.enums.CategoryStatus;
import java.util.List;

public interface CategoryService {

    CategoryResponse createCategory(CategoryAddRequest request);

    CategoryResponse updateCategory(Long id, CategoryUpdateRequest request);

    CategoryResponse getCategoryById(Long id);

    List<CategoryResponse> getCategoriesByRestaurant(Long restaurantId);

    List<CategoryResponse> getCategoriesByRestaurantAndStatus(
            Long restaurantId, CategoryStatus status);

    void changeStatus(Long id, CategoryStatus status);

    void deleteCategory(Long id);
}
