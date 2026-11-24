package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.dish.DishAddRequest;
import com.group.restaurant.dtos.request.dish.DishUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.dish.DishResponse;
import com.group.restaurant.enums.BranchStatus;
import com.group.restaurant.enums.DishStatus;
import com.group.restaurant.enums.StockStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.DishMapper;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Category;
import com.group.restaurant.models.Dish;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.repositories.BranchDishStockRepository;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.CategoryRepository;
import com.group.restaurant.repositories.DishOverrideRepository;
import com.group.restaurant.repositories.DishRepository;
import com.group.restaurant.repositories.OrderDetailRepository;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.services.DishService;
import com.group.restaurant.utils.TenantGuard;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DishServiceImpl implements DishService {

    private final DishRepository dishRepository;
    private final RestaurantRepository restaurantRepository;
    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;
    private final DishOverrideRepository dishOverrideRepository;
    private final BranchDishStockRepository branchDishStockRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final DishMapper dishMapper;
    private final TenantGuard tenantGuard;

    @Override
    public DishResponse createDish(DishAddRequest request) {
        tenantGuard.assertRestaurant(request.getRestaurantId());
        Restaurant restaurant =
                restaurantRepository
                        .findById(request.getRestaurantId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy nhà hàng với ID: "
                                                        + request.getRestaurantId()));

        Branch branch =
                request.getBranchId() == null
                        ? null
                        : branchRepository
                                .findById(request.getBranchId())
                                .orElseThrow(
                                        () ->
                                                new ResourceNotFoundException(
                                                        "Không tìm thấy chi nhánh với ID: "
                                                                + request.getBranchId()));
        tenantGuard.assertMenuDishWrite(restaurant.getId(), branch);
        Category category = findMenuCategory(request.getCategoryId(), restaurant.getId());

        Dish dish = dishMapper.toEntity(request);
        dish.setRestaurant(restaurant);
        dish.setCategory(category);
        dish.setStatus(DishStatus.ACTIVE);
        if (dish.getSoldByWeight() == null) {
            dish.setSoldByWeight(false);
        }

        dish.setBranch(branch);

        Dish saved = dishRepository.save(dish);
        return dishMapper.toResponse(saved);
    }

    @Override
    public DishResponse updateDish(Long id, DishUpdateRequest request) {
        Dish dish = findWritableDish(id);

        if (request.getCategoryId() != null) {
            dish.setCategory(
                    findMenuCategory(request.getCategoryId(), dish.getRestaurant().getId()));
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
    public PageResponse<DishResponse> getDishesByRestaurant(
            Long restaurantId, DishStatus status, Pageable pageable) {
        tenantGuard.assertRestaurant(restaurantId);
        List<Dish> dishes;

        if (status != null) {
            dishes = dishRepository.findByRestaurantIdAndStatus(restaurantId, status);
        } else {
            dishes = dishRepository.findByRestaurantId(restaurantId);
        }

        List<DishResponse> responses =
                dishes.stream().map(dishMapper::toResponse).collect(Collectors.toList());

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
        tenantGuard.assertBranch(branchId);
        return dishRepository.findByBranchId(branchId).stream()
                .map(dishMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DishResponse> getDishesByCategory(Long categoryId) {
        return dishRepository.findByCategoryId(categoryId).stream()
                .map(dishMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void changeStatus(Long id, DishStatus status) {
        Dish dish = findWritableDish(id);
        dish.setStatus(status);
        dishRepository.save(dish);
    }

    /**
     * QĐ-05 — đếm số chi nhánh ĐANG BÁN món này.
     *
     * Trước đây hàm này đếm số chi nhánh có bản **đè giá** (`DishOverride`), nên một chi nhánh
     * bán món mà không đè giá thì không được tính: cảnh báo nhỏ hơn thực tế, người dùng yên tâm
     * nhầm. FR-MNU-04 nói món ngừng bán ở cấp nhà hàng **biến mất ở mọi chi nhánh**, nên con số
     * phải là số chi nhánh thật sự đang bán.
     *
     * Một chi nhánh KHÔNG được tính khi: bị ẩn món bằng `DishOverride.isHidden`, hoặc đang đánh
     * dấu hết món (`BranchDishStock.status = OUT_OF_STOCK`), hoặc chi nhánh đang ngừng hoạt động.
     *
     * Món RIÊNG của một chi nhánh (`dish.branch != null`) thì chỉ chi nhánh đó bán.
     */
    @Override
    public int countBranchesSellingDish(Long id) {
        Dish dish = findById(id);

        if (dish.getBranch() != null) {
            return dish.getBranch().getStatus() == BranchStatus.ACTIVE ? 1 : 0;
        }

        Long restaurantId = dish.getRestaurant() != null ? dish.getRestaurant().getId() : null;
        if (restaurantId == null) {
            return 0;
        }

        Set<Long> hiddenBranchIds =
                dishOverrideRepository.findByDishId(id).stream()
                        .filter(override -> Boolean.TRUE.equals(override.getIsHidden()))
                        .map(
                                override ->
                                        override.getBranch() != null
                                                ? override.getBranch().getId()
                                                : null)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        return (int)
                branchRepository
                        .findByRestaurantIdAndStatus(restaurantId, BranchStatus.ACTIVE)
                        .stream()
                        .filter(branch -> !hiddenBranchIds.contains(branch.getId()))
                        .filter(
                                branch ->
                                        branchDishStockRepository
                                                .findByBranchIdAndDishId(branch.getId(), id)
                                                .map(
                                                        stock ->
                                                                stock.getStatus()
                                                                        != StockStatus.OUT_OF_STOCK)
                                                .orElse(true))
                        .count();
    }

    @Override
    public void deleteDish(Long id) {
        Dish dish = findWritableDish(id);

        // FR-MNU-02 + BR-09: hóa đơn đã phát hành là BẤT BIẾN. Món đã nằm trên một hóa
        // đơn thì xóa mềm sẽ làm hỏng dữ liệu tra cứu về sau. Xét MỌI trạng thái hóa đơn
        // và MỌI chi nhánh — món gốc bán ở tất cả chi nhánh.
        if (orderDetailRepository.existsOnAnyInvoice(id)) {
            throw new ConflictException("Món đã phát sinh hoá đơn, chỉ có thể ngừng bán");
        }

        // Cascade soft delete: DishOverride
        dishOverrideRepository
                .findByDishId(id)
                .forEach(
                        dishOverride -> {
                            dishOverride.softDelete(null);
                            dishOverrideRepository.save(dishOverride);
                        });

        dish.softDelete(null);
        dishRepository.save(dish);
    }

    private Dish findWritableDish(Long id) {
        Dish dish = findById(id);
        tenantGuard.assertMenuDishWrite(dish.getRestaurant().getId(), dish.getBranch());
        return dish;
    }

    private Category findMenuCategory(Long id, Long restaurantId) {
        Category category =
                categoryRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy danh mục với ID: " + id));
        if (!restaurantId.equals(category.getRestaurant().getId())) {
            throw new AccessDeniedException("Danh mục không thuộc nhà hàng của món");
        }
        return category;
    }

    private Dish findById(Long id) {
        Dish dish =
                dishRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy món ăn với ID: " + id));
        tenantGuard.assertRestaurant(
                dish.getRestaurant() != null ? dish.getRestaurant().getId() : null);
        return dish;
    }
}
