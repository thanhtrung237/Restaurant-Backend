package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.category.CategoryAddRequest;
import com.group.restaurant.dtos.request.category.CategoryUpdateRequest;
import com.group.restaurant.dtos.response.category.CategoryResponse;
import com.group.restaurant.enums.CategoryStatus;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.CategoryMapper;
import com.group.restaurant.models.Category;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.repositories.CategoryRepository;
import com.group.restaurant.repositories.DishOverrideRepository;
import com.group.restaurant.repositories.DishRepository;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.services.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final DishRepository dishRepository;
    private final DishOverrideRepository dishOverrideRepository;
    private final CategoryMapper categoryMapper;

    @Override
    public CategoryResponse createCategory(CategoryAddRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhà hàng với ID: " + request.getRestaurantId()));

        Category category = categoryMapper.toEntity(request);
        category.setRestaurant(restaurant);
        category.setStatus(CategoryStatus.ACTIVE);

        Category saved = categoryRepository.save(category);
        return categoryMapper.toResponse(saved);
    }

    @Override
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request) {
        Category category = findById(id);
        categoryMapper.updateEntity(category, request);
        Category updated = categoryRepository.save(category);
        return categoryMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category category = findById(id);
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByRestaurant(Long restaurantId) {
        return categoryRepository.findByRestaurantIdOrderByDisplayOrderAsc(restaurantId)
                .stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByRestaurantAndStatus(Long restaurantId, CategoryStatus status) {
        return categoryRepository.findByRestaurantIdAndStatus(restaurantId, status)
                .stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void changeStatus(Long id, CategoryStatus status) {
        Category category = findById(id);
        category.setStatus(status);
        categoryRepository.save(category);
    }

    @Override
    public void deleteCategory(Long id) {
        Category category = findById(id);

        // Cascade soft delete: Dish → DishOverride
        dishRepository.findByCategoryId(id).forEach(dish -> {
            dishOverrideRepository.findByDishId(dish.getId()).forEach(dishOverride -> {
                dishOverride.softDelete(null);
                dishOverrideRepository.save(dishOverride);
            });
            dish.softDelete(null);
            dishRepository.save(dish);
        });

        category.softDelete(null);
        categoryRepository.save(category);
    }

    private Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với ID: " + id));
    }
}
