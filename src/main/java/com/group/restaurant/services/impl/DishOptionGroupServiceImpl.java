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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DishOptionGroupServiceImpl implements DishOptionGroupService {

    private final DishOptionGroupRepository dishOptionGroupRepository;
    private final DishRepository dishRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final DishOptionGroupMapper dishOptionGroupMapper;

    @Override
    public DishOptionGroupResponse linkDishToOptionGroup(DishOptionGroupAddRequest request) {
        // Validate dish exists
        Dish dish = dishRepository.findById(request.getDishId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy món ăn với ID: " + request.getDishId()));

        // Validate option group exists
        OptionGroup optionGroup = optionGroupRepository.findById(request.getOptionGroupId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhóm tùy chọn với ID: " + request.getOptionGroupId()));

        // Check duplicate link
        if (dishOptionGroupRepository.existsByDishIdAndOptionGroupId(
                request.getDishId(), request.getOptionGroupId())) {
            throw new ConflictException("Món ăn đã được liên kết với nhóm tùy chọn này");
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
        List<DishOptionGroup> dishOptionGroups = dishOptionGroupRepository.findByOptionGroupId(optionGroupId);
        return dishOptionGroups.stream()
                .map(dishOptionGroupMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void unlinkDishFromOptionGroup(Long id) {
        DishOptionGroup dishOptionGroup = findById(id);
        
        // Soft delete
        dishOptionGroup.softDelete(null);
        dishOptionGroupRepository.save(dishOptionGroup);
    }

    private DishOptionGroup findById(Long id) {
        return dishOptionGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy liên kết món-nhóm tùy chọn với ID: " + id));
    }
}
