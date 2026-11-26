package com.group.restaurant.services.impl;

import com.group.restaurant.services.OrderNotificationService;
import com.group.restaurant.dtos.response.branchDishStock.BranchDishStockResponse;
import com.group.restaurant.enums.OperationalPermission;
import com.group.restaurant.enums.StockStatus;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.BranchDishStockMapper;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.BranchDishStock;
import com.group.restaurant.models.Dish;
import com.group.restaurant.repositories.BranchDishStockRepository;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.DishRepository;
import com.group.restaurant.services.BranchDishStockService;
import com.group.restaurant.services.OperationalPermissionService;
import com.group.restaurant.utils.TenantGuard;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class BranchDishStockServiceImpl implements BranchDishStockService {

    private final BranchDishStockRepository stockRepository;
    private final OrderNotificationService orderNotificationService;
    private final BranchRepository branchRepository;
    private final DishRepository dishRepository;
    private final BranchDishStockMapper stockMapper;
    private final TenantGuard tenantGuard;
    private final OperationalPermissionService permissions;

    @Override
    public BranchDishStockResponse toggleStock(Long branchId, Long dishId) {
        permissions.require(branchId, OperationalPermission.MANAGE_STOCK);
        // Lấy hoặc tạo mới bản ghi stock
        BranchDishStock stock =
                stockRepository
                        .findByBranchIdAndDishId(branchId, dishId)
                        .orElseGet(() -> createNewStock(branchId, dishId));

        // Toggle: AVAILABLE → OUT_OF_STOCK và ngược lại
        if (stock.getStatus() == StockStatus.AVAILABLE) {
            stock.setStatus(StockStatus.OUT_OF_STOCK);
            log.info("Marked dish {} as OUT_OF_STOCK at branch {}", dishId, branchId);
        } else {
            stock.setStatus(StockStatus.AVAILABLE);
            log.info("Marked dish {} as AVAILABLE at branch {}", dishId, branchId);
        }

        BranchDishStock saved = stockRepository.save(stock);
        orderNotificationService.notifyMenuUpdated(stock.getBranch().getRestaurant().getId());
        return stockMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchDishStockResponse> getOutOfStockByBranch(Long branchId) {
        tenantGuard.assertBranch(branchId);
        return stockRepository.findByBranchIdAndStatus(branchId, StockStatus.OUT_OF_STOCK).stream()
                .map(stockMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchDishStockResponse> getAllStockByBranch(Long branchId) {
        tenantGuard.assertBranch(branchId);
        return stockRepository.findByBranchId(branchId).stream()
                .map(stockMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public int restoreAllStock(Long branchId) {
        permissions.require(branchId, OperationalPermission.MANAGE_STOCK);
        return resetStockForBranch(branchId);
    }

    @Override
    public int resetStockForBranch(Long branchId) {
        // FR-MNU-03: Mở bán lại tất cả món đang hết tại chi nhánh
        int count = stockRepository.resetStockForBranch(branchId);
        if (count > 0) {
            branchRepository.findById(branchId)
                    .ifPresent(branch -> orderNotificationService.notifyMenuUpdated(branch.getRestaurant().getId()));
        }
        log.info("Restored {} dishes to AVAILABLE at branch {}", count, branchId);
        return count;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAvailable(Long branchId, Long dishId) {
        // Nếu không có bản ghi stock → mặc định là AVAILABLE
        return stockRepository
                .findByBranchIdAndDishId(branchId, dishId)
                .map(stock -> stock.getStatus() == StockStatus.AVAILABLE)
                .orElse(true);
    }

    private BranchDishStock createNewStock(Long branchId, Long dishId) {
        Branch branch =
                branchRepository
                        .findById(branchId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy chi nhánh với ID: " + branchId));
        Dish dish =
                dishRepository
                        .findById(dishId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy món ăn với ID: " + dishId));
        if (!branch.getRestaurant().getId().equals(dish.getRestaurant().getId())
                || (dish.getBranch() != null && !branchId.equals(dish.getBranch().getId()))) {
            throw new ResourceNotFoundException("Món không thuộc thực đơn của chi nhánh.");
        }

        return BranchDishStock.builder()
                .branch(branch)
                .dish(dish)
                .status(StockStatus.AVAILABLE)
                .build();
    }
}
