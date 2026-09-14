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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

    @Override
    public DishOverrideResponse createDishOverride(DishOverrideAddRequest request) {
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chi nhánh với ID: " + request.getBranchId()));

        Dish dish = dishRepository.findById(request.getDishId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy món ăn với ID: " + request.getDishId()));

        if (dishOverrideRepository.findByBranchIdAndDishId(request.getBranchId(), request.getDishId()).isPresent()) {
            throw new ConflictException(
                    "Đã tồn tại tùy chỉnh giá cho chi nhánh ID: " + request.getBranchId() + " và món ID: "
                            + request.getDishId());
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
        DishOverride dishOverride = findById(id);
        dishOverrideMapper.updateEntity(dishOverride, request);
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
        DishOverride dishOverride = findById(id);
        dishOverride.softDelete(null);
        dishOverrideRepository.save(dishOverride);
    }

    private DishOverride findById(Long id) {
        return dishOverrideRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tùy chỉnh món với ID: " + id));
    }
}
