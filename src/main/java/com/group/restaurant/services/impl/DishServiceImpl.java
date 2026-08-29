package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.dish.DishAddRequest;
import com.group.restaurant.dtos.request.dish.DishUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.dish.DishResponse;
import com.group.restaurant.enums.DishStatus;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.DishMapper;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Category;
import com.group.restaurant.models.Dish;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.CategoryRepository;
import com.group.restaurant.repositories.DishOverrideRepository;
import com.group.restaurant.repositories.DishRepository;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.services.DishService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DishServiceImpl implements DishService {

    private final DishRepository dishRepository;
    private final RestaurantRepository restaurantRepository;
    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;
    private final DishOverrideRepository dishOverrideRepository;
    private final DishMapper dishMapper;

    @Override
    public DishResponse createDish(DishAddRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhà hàng với ID: " + request.getRestaurantId()));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy danh mục với ID: " + request.getCategoryId()));

        Dish dish = dishMapper.toEntity(request);
        dish.setRestaurant(restaurant);
        dish.setCategory(category);
        dish.setStatus(DishStatus.ACTIVE);
        if (dish.getSoldByWeight() == null) {
            dish.setSoldByWeight(false);
        }

        // FR-MNU-04: Nếu có branchId → món riêng chi nhánh
        if (request.getBranchId() != null) {
            Branch branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy chi nhánh với ID: " + request.getBranchId()));
            dish.setBranch(branch);
        }

        Dish saved = dishRepository.save(dish);
        return dishMapper.toResponse(saved);
    }

    @Override
    public DishResponse updateDish(Long id, DishUpdateRequest request) {
        Dish dish = findById(id);

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy danh mục với ID: " + request.getCategoryId()));
            dish.setCategory(category);
        }

        dishMapper.updateEntity(dish, request);
        Dish updated = dishRepository.save(dish);
        return dishMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public DishResponse getDishById(Long id) {
        Dish dish = findById(id);
        return dishMapper.toResponse(dish);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DishResponse> getDishesByRestaurant(Long restaurantId, DishStatus status, Pageable pageable) {
        List<Dish> dishes;

        if (status != null) {
            dishes = dishRepository.findByRestaurantIdAndStatus(restaurantId, status);
        } else {
            dishes = dishRepository.findByRestaurantId(restaurantId);
        }

        List<DishResponse> responses = dishes.stream()
                .map(dishMapper::toResponse)
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), responses.size());
        List<DishResponse> pageContent = responses.subList(start, end);

        Page<DishResponse> page = new PageImpl<>(pageContent, pageable, responses.size());

        return PageResponse.<DishResponse>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DishResponse> getDishesByBranch(Long branchId) {
        return dishRepository.findByBranchId(branchId)
                .stream()
                .map(dishMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DishResponse> getDishesByCategory(Long categoryId) {
        return dishRepository.findByCategoryId(categoryId)
                .stream()
                .map(dishMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void changeStatus(Long id, DishStatus status) {
        Dish dish = findById(id);
        dish.setStatus(status);
        dishRepository.save(dish);
    }

    @Override
    public int countBranchesAffectedByDeletion(Long id) {
        // FR-MNU-04: Đếm số chi nhánh đang có bản đè cho món này → sẽ bị ảnh hưởng
        return dishOverrideRepository.findByDishId(id).size();
    }

    @Override
    public void deleteDish(Long id) {
        Dish dish = findById(id);

        // Cascade soft delete: DishOverride
        dishOverrideRepository.findByDishId(id).forEach(dishOverride -> {
            dishOverride.softDelete(null);
            dishOverrideRepository.save(dishOverride);
        });

        dish.softDelete(null);
        dishRepository.save(dish);
    }

    private Dish findById(Long id) {
        return dishRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với ID: " + id));
    }
}
