package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.dishOverride.DishOverrideAddRequest;
import com.group.restaurant.dtos.request.dishOverride.DishOverrideUpdateRequest;
import com.group.restaurant.dtos.response.dishOverride.DishOverrideResponse;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.DishOverrideMapper;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Dish;
import com.group.restaurant.models.DishOverride;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.DishOverrideRepository;
import com.group.restaurant.repositories.DishRepository;
import com.group.restaurant.services.DishOverrideService;
import com.group.restaurant.utils.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DishOverrideServiceImpl implements DishOverrideService {

    private final DishOverrideRepository dishOverrideRepository;
    private final BranchRepository branchRepository;
    private final DishRepository dishRepository;
    private final DishOverrideMapper dishOverrideMapper;
    private final TenantGuard tenantGuard;

    @Override
    public DishOverrideResponse createDishOverride(DishOverrideAddRequest request) {
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chi nhánh với ID: " + request.getBranchId()));

        tenantGuard.assertBranchAssigned(branch.getId(), TenantGuard.ROLE_BRANCH_MANAGER);
        Dish dish = lockDish(request.getDishId());
        if (!branch.getRestaurant().getId().equals(dish.getRestaurant().getId())) {
            throw new AccessDeniedException("Món ăn không thuộc nhà hàng của chi nhánh");
        }

        var previousOverride = dishOverrideRepository.findIncludingDeleted(branch.getId(), dish.getId());
        if (previousOverride.isPresent()) {
            DishOverride override = previousOverride.get();
            if (!Boolean.TRUE.equals(override.getIsDeleted())) {
                throw new ConflictException("Món đã có ghi đè tại chi nhánh này");
            }
            override.setOverridePrice(request.getOverridePrice());
            override.setIsHidden(Boolean.TRUE.equals(request.getIsHidden()));
            override.setIsDeleted(false);
            override.setDeletedAt(null);
            override.setDeletedBy(null);
            return dishOverrideMapper.toResponse(override);
        }

        DishOverride dishOverride = dishOverrideMapper.toEntity(request);
        dishOverride.setBranch(branch);
        dishOverride.setDish(dish);
        if (dishOverride.getIsHidden() == null) {
            dishOverride.setIsHidden(false);
        }

        DishOverride saved = dishOverrideRepository.save(dishOverride);
        return dishOverrideMapper.toResponse(saved);
    }

    @Override
    public DishOverrideResponse updateDishOverride(Long id, DishOverrideUpdateRequest request) {
        DishOverride dishOverride = findWritableOverride(id);
        dishOverrideMapper.updateEntity(dishOverride, request);
        if (request.isClearOverridePrice()) {
            dishOverride.setOverridePrice(null);
        }
        DishOverride updated = dishOverrideRepository.save(dishOverride);
        return dishOverrideMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public DishOverrideResponse getDishOverrideById(Long id) {
        DishOverride dishOverride = findById(id);
        return dishOverrideMapper.toResponse(dishOverride);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DishOverrideResponse> getDishOverridesByBranch(Long branchId) {
        tenantGuard.assertBranch(branchId);
        return dishOverrideRepository.findByBranchId(branchId)
                .stream()
                .map(dishOverrideMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DishOverrideResponse> getDishOverridesByDish(Long dishId) {
        return dishOverrideRepository.findByDishId(dishId)
                .stream()
                .map(dishOverrideMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteDishOverride(Long id) {
        DishOverride dishOverride = findWritableOverride(id);
        dishOverride.softDelete(null);
        dishOverrideRepository.save(dishOverride);
    }

    private DishOverride findWritableOverride(Long id) {
        DishOverride override = findById(id);
        tenantGuard.assertBranchAssigned(override.getBranch().getId(), TenantGuard.ROLE_BRANCH_MANAGER);
        lockDish(override.getDish().getId());
        return override;
    }

    private Dish lockDish(Long id) {
        return dishRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với ID: " + id));
    }

    private DishOverride findById(Long id) {
        DishOverride override = dishOverrideRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tùy chỉnh món với ID: " + id));
        tenantGuard.assertBranch(override.getBranch());
        return override;
    }
}
