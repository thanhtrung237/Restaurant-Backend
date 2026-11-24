package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.dishOptionGroup.DishOptionGroupAddRequest;
import com.group.restaurant.dtos.response.dishOptionGroup.DishOptionGroupResponse;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.DishOptionGroupMapper;
import com.group.restaurant.models.Dish;
import com.group.restaurant.models.DishOptionGroup;
import com.group.restaurant.models.OptionGroup;
import com.group.restaurant.repositories.DishOptionGroupRepository;
import com.group.restaurant.repositories.DishRepository;
import com.group.restaurant.repositories.OptionGroupRepository;
import com.group.restaurant.services.DishOptionGroupService;
import com.group.restaurant.utils.TenantGuard;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DishOptionGroupServiceImpl implements DishOptionGroupService {

    private final DishOptionGroupRepository dishOptionGroupRepository;
    private final DishRepository dishRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final DishOptionGroupMapper dishOptionGroupMapper;
    private final TenantGuard tenantGuard;

    @Override
    public DishOptionGroupResponse linkDishToOptionGroup(DishOptionGroupAddRequest request) {
        // Validate dish exists
        Dish dish = lockDish(request.getDishId());

        // Validate option group exists
        OptionGroup optionGroup =
                optionGroupRepository
                        .findById(request.getOptionGroupId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy nhóm tùy chọn với ID: "
                                                        + request.getOptionGroupId()));

        // Món và nhóm tùy chọn phải cùng thuộc nhà hàng của người đăng nhập —
        // chặn liên kết chéo giữa 2 nhà hàng.
        tenantGuard.assertMenuDishWrite(dish.getRestaurant().getId(), dish.getBranch());
        tenantGuard.assertRestaurant(
                optionGroup.getRestaurant() != null ? optionGroup.getRestaurant().getId() : null);

        var existing =
                dishOptionGroupRepository.findIncludingDeleted(dish.getId(), optionGroup.getId());
        if (existing.isPresent()) {
            DishOptionGroup link = existing.get();
            if (!Boolean.TRUE.equals(link.getIsDeleted())) {
                throw new ConflictException("Món ăn đã được liên kết với nhóm tùy chọn này");
            }
            link.setIsDeleted(false);
            link.setDeletedAt(null);
            link.setDeletedBy(null);
            return dishOptionGroupMapper.toResponse(link);
        }

        DishOptionGroup dishOptionGroup = dishOptionGroupMapper.toEntity(request);
        dishOptionGroup.setDish(dish);
        dishOptionGroup.setOptionGroup(optionGroup);

        DishOptionGroup saved = dishOptionGroupRepository.save(dishOptionGroup);
        return dishOptionGroupMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DishOptionGroupResponse getDishOptionGroupById(Long id) {
        DishOptionGroup dishOptionGroup = findById(id);
        return dishOptionGroupMapper.toResponse(dishOptionGroup);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DishOptionGroupResponse> getDishOptionGroupsByDish(Long dishId) {
        List<DishOptionGroup> dishOptionGroups = dishOptionGroupRepository.findByDishId(dishId);
        return dishOptionGroups.stream()
                .map(dishOptionGroupMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DishOptionGroupResponse> getDishOptionGroupsByOptionGroup(Long optionGroupId) {
        List<DishOptionGroup> dishOptionGroups =
                dishOptionGroupRepository.findByOptionGroupId(optionGroupId);
        return dishOptionGroups.stream()
                .map(dishOptionGroupMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void unlinkDishFromOptionGroup(Long id) {
        DishOptionGroup dishOptionGroup = findById(id);
        Dish dish = lockDish(dishOptionGroup.getDish().getId());
        tenantGuard.assertMenuDishWrite(dish.getRestaurant().getId(), dish.getBranch());

        // Soft delete
        dishOptionGroup.softDelete(null);
        dishOptionGroupRepository.save(dishOptionGroup);
    }

    private Dish lockDish(Long id) {
        return dishRepository
                .findByIdForUpdate(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy món ăn với ID: " + id));
    }

    /** Chốt tenant tại finder: phủ getDishOptionGroupById / unlinkDishFromOptionGroup. */
    private DishOptionGroup findById(Long id) {
        DishOptionGroup link =
                dishOptionGroupRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy liên kết món-nhóm tùy chọn với ID: "
                                                        + id));
        Dish dish = link.getDish();
        tenantGuard.assertRestaurant(
                dish != null && dish.getRestaurant() != null ? dish.getRestaurant().getId() : null);
        return link;
    }
}
