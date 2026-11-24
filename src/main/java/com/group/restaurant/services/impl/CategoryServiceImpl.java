package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.category.CategoryAddRequest;
import com.group.restaurant.dtos.request.category.CategoryUpdateRequest;
import com.group.restaurant.dtos.response.category.CategoryResponse;
import com.group.restaurant.enums.CategoryStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.CategoryMapper;
import com.group.restaurant.models.Category;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.repositories.CategoryRepository;
import com.group.restaurant.repositories.DishRepository;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.services.CategoryService;
import com.group.restaurant.utils.TenantGuard;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final DishRepository dishRepository;
    private final CategoryMapper categoryMapper;
    private final TenantGuard tenantGuard;

    @Override
    public CategoryResponse createCategory(CategoryAddRequest request) {
        Restaurant restaurant =
                restaurantRepository
                        .findById(request.getRestaurantId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy nhà hàng với ID: "
                                                        + request.getRestaurantId()));

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
        tenantGuard.assertRestaurant(restaurantId);
        return categoryRepository.findByRestaurantIdOrderByDisplayOrderAsc(restaurantId).stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByRestaurantAndStatus(
            Long restaurantId, CategoryStatus status) {
        tenantGuard.assertRestaurant(restaurantId);
        return categoryRepository.findByRestaurantIdAndStatus(restaurantId, status).stream()
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

        // FR-MNU-01: "Không cho xóa danh mục còn món; phải chuyển món sang danh mục khác
        // trước". Trước đây BE xóa mềm KÉO THEO toàn bộ Dish + DishOverride, nên lỡ bấm
        // xóa một danh mục là mất cả thực đơn của danh mục đó. Đếm cả món riêng chi nhánh
        // (branch != null) vì chúng cũng thuộc danh mục này.
        // `findByCategoryId` đã loại món đã xóa mềm nhờ @SQLRestriction trên Dish.
        long dishCount = dishRepository.findByCategoryId(id).size();
        if (dishCount > 0) {
            throw new ConflictException("Danh mục còn " + dishCount + " món");
        }

        category.softDelete(null);
        categoryRepository.save(category);
    }

    /** Chốt tenant tại finder: phủ getCategoryById / updateCategory / changeStatus / deleteCategory. */
    private Category findById(Long id) {
        Category category =
                categoryRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy danh mục với ID: " + id));
        tenantGuard.assertRestaurant(
                category.getRestaurant() != null ? category.getRestaurant().getId() : null);
        return category;
    }
}
