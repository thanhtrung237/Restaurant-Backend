package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.response.branch.BranchMenuItemResponse;
import com.group.restaurant.enums.StockStatus;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Dish;
import com.group.restaurant.models.DishOverride;
import com.group.restaurant.repositories.BranchDishStockRepository;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.DishOverrideRepository;
import com.group.restaurant.repositories.DishRepository;
import com.group.restaurant.services.BranchMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchMenuServiceImpl implements BranchMenuService {

    private final BranchRepository branchRepository;
    private final DishRepository dishRepository;
    private final DishOverrideRepository dishOverrideRepository;
    private final BranchDishStockRepository stockRepository;

    @Override
    public List<BranchMenuItemResponse> getMenuForCustomer(Long branchId) {
        List<BranchMenuItemResponse> allItems = buildMenuItems(branchId);

        // Customer chỉ thấy: không bị ẩn + còn hàng (BR-05)
        return allItems.stream()
                .filter(item -> !item.isHidden())
                .filter(item -> item.getStockStatus() == StockStatus.AVAILABLE)
                .collect(Collectors.toList());
    }

    @Override
    public List<BranchMenuItemResponse> getMenuOverviewForAdmin(Long branchId) {
        // Admin thấy tất cả (cả ẩn, cả hết hàng)
        return buildMenuItems(branchId);
    }

   
    private List<BranchMenuItemResponse> buildMenuItems(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chi nhánh với ID: " + branchId));

        Map<Long, DishOverride> overrideMap = dishOverrideRepository.findByBranchId(branchId)
                .stream()
                .collect(Collectors.toMap(o -> o.getDish().getId(), o -> o));

        Map<Long, StockStatus> stockMap = stockRepository.findByBranchId(branchId)
                .stream()
                .collect(Collectors.toMap(
                        s -> s.getDish().getId(),
                        s -> s.getStatus()
                ));

        List<BranchMenuItemResponse> result = new ArrayList<>();

    
        List<Dish> restaurantDishes = dishRepository
                .findByRestaurantIdAndBranchIsNull(branch.getRestaurant().getId());

        for (Dish dish : restaurantDishes) {
            DishOverride override = overrideMap.get(dish.getId());

            boolean isHidden = override != null && Boolean.TRUE.equals(override.getIsHidden());
            boolean hasOverride = override != null && override.getOverridePrice() != null;
            BigDecimal overridePrice = hasOverride ? override.getOverridePrice() : null;

            // BR-17: Giá hiệu lực
            BigDecimal effectivePrice = hasOverride ? overridePrice : dish.getBasePrice();

   
            BigDecimal priceDiff = hasOverride
                    ? overridePrice.subtract(dish.getBasePrice())
                    : BigDecimal.ZERO;

            StockStatus stock = stockMap.getOrDefault(dish.getId(), StockStatus.AVAILABLE);

            result.add(BranchMenuItemResponse.builder()
                    .dishId(dish.getId())
                    .dishName(dish.getName())
                    .description(dish.getDescription())
                    .imageUrl(dish.getImageUrl())
                    .kitchenStationType(dish.getKitchenStationType())
                    .soldByWeight(dish.getSoldByWeight())
                    .categoryId(dish.getCategory() != null ? dish.getCategory().getId() : null)
                    .categoryName(dish.getCategory() != null ? dish.getCategory().getName() : null)
                    .basePrice(dish.getBasePrice())
                    .overridePrice(overridePrice)
                    .effectivePrice(effectivePrice)
                    .priceDifference(priceDiff)
                    .hasOverride(hasOverride)
                    .isHidden(isHidden)
                    .stockStatus(stock)
                    .isBranchSpecific(false)
                    .build());
        }


        List<Dish> branchSpecificDishes = dishRepository.findByBranchId(branchId);

        for (Dish dish : branchSpecificDishes) {
            StockStatus stock = stockMap.getOrDefault(dish.getId(), StockStatus.AVAILABLE);

            result.add(BranchMenuItemResponse.builder()
                    .dishId(dish.getId())
                    .dishName(dish.getName())
                    .description(dish.getDescription())
                    .imageUrl(dish.getImageUrl())
                    .kitchenStationType(dish.getKitchenStationType())
                    .soldByWeight(dish.getSoldByWeight())
                    .categoryId(dish.getCategory() != null ? dish.getCategory().getId() : null)
                    .categoryName(dish.getCategory() != null ? dish.getCategory().getName() : null)
                    .basePrice(dish.getBasePrice())
                    .overridePrice(null)
                    .effectivePrice(dish.getBasePrice())
                    .priceDifference(BigDecimal.ZERO)
                    .hasOverride(false)
                    .isHidden(false)
                    .stockStatus(stock)
                    .isBranchSpecific(true)
                    .build());
        }

        return result;
    }
}
