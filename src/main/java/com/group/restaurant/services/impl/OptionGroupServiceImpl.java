package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.optionGroup.OptionGroupAddRequest;
import com.group.restaurant.dtos.request.optionGroup.OptionGroupUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.optionGroup.OptionGroupResponse;
import com.group.restaurant.enums.SelectionType;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.OptionGroupMapper;
import com.group.restaurant.models.OptionGroup;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.repositories.DishOptionGroupRepository;
import com.group.restaurant.repositories.OptionGroupRepository;
import com.group.restaurant.repositories.OptionItemRepository;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.services.OptionGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OptionGroupServiceImpl implements OptionGroupService {

    private final OptionGroupRepository optionGroupRepository;
    private final RestaurantRepository restaurantRepository;
    private final OptionItemRepository optionItemRepository;
    private final DishOptionGroupRepository dishOptionGroupRepository;
    private final OptionGroupMapper optionGroupMapper;

    @Override
    public OptionGroupResponse createOptionGroup(OptionGroupAddRequest request) {
        // Validate restaurant exists
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhà hàng với ID: " + request.getRestaurantId()));

        // Check duplicate name in same restaurant
        if (optionGroupRepository.existsByRestaurantIdAndName(request.getRestaurantId(), request.getName())) {
            throw new ConflictException("Nhóm tùy chọn với tên '" + request.getName() + "' đã tồn tại trong nhà hàng này");
        }

        // Validate minSelect/maxSelect logic
        validateSelectionRange(request.getSelectionType(), request.getMinSelect(), request.getMaxSelect());

        OptionGroup optionGroup = optionGroupMapper.toEntity(request);
        optionGroup.setRestaurant(restaurant);

        OptionGroup saved = optionGroupRepository.save(optionGroup);
        return optionGroupMapper.toResponse(saved);
    }

    @Override
    public OptionGroupResponse updateOptionGroup(Long id, OptionGroupUpdateRequest request) {
        OptionGroup optionGroup = findById(id);

        // Check duplicate name if name is being changed
        if (request.getName() != null && !request.getName().equals(optionGroup.getName())) {
            if (optionGroupRepository.existsByRestaurantIdAndName(
                    optionGroup.getRestaurant().getId(), request.getName())) {
                throw new ConflictException("Nhóm tùy chọn với tên '" + request.getName() + "' đã tồn tại trong nhà hàng này");
            }
        }

        // Validate minSelect/maxSelect if changed
        SelectionType selectionType = request.getSelectionType() != null ? 
                request.getSelectionType() : optionGroup.getSelectionType();
        int minSelect = request.getMinSelect() != null ? request.getMinSelect() : optionGroup.getMinSelect();
        int maxSelect = request.getMaxSelect() != null ? request.getMaxSelect() : optionGroup.getMaxSelect();
        
        validateSelectionRange(selectionType, minSelect, maxSelect);

        optionGroupMapper.updateEntity(optionGroup, request);
        OptionGroup saved = optionGroupRepository.save(optionGroup);
        return optionGroupMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OptionGroupResponse getOptionGroupById(Long id) {
        OptionGroup optionGroup = findById(id);
        return optionGroupMapper.toResponse(optionGroup);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OptionGroupResponse> getOptionGroupsByRestaurant(Long restaurantId) {
        List<OptionGroup> optionGroups = optionGroupRepository.findByRestaurantId(restaurantId);
        return optionGroups.stream()
                .map(optionGroupMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OptionGroupResponse> getAllOptionGroups(Long restaurantId, String keyword, Pageable pageable) {
        Page<OptionGroup> page;
        
        if (restaurantId != null && keyword != null && !keyword.trim().isEmpty()) {
            page = optionGroupRepository.findByRestaurantIdAndNameContainingIgnoreCase(
                    restaurantId, keyword.trim(), pageable);
        } else if (restaurantId != null) {
            page = optionGroupRepository.findByRestaurantId(restaurantId, pageable);
        } else if (keyword != null && !keyword.trim().isEmpty()) {
            page = optionGroupRepository.findByNameContainingIgnoreCase(keyword.trim(), pageable);
        } else {
            page = optionGroupRepository.findAll(pageable);
        }

        List<OptionGroupResponse> content = page.getContent().stream()
                .map(optionGroupMapper::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<OptionGroupResponse>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    @Override
    public void deleteOptionGroup(Long id) {
        OptionGroup optionGroup = findById(id);
        
        // Cascade soft delete: OptionItems
        optionItemRepository.findByOptionGroupId(id).forEach(item -> {
            item.softDelete(null);
            optionItemRepository.save(item);
        });

        // Cascade soft delete: DishOptionGroup links
        dishOptionGroupRepository.findByOptionGroupId(id).forEach(link -> {
            link.softDelete(null);
            dishOptionGroupRepository.save(link);
        });

        // Soft delete the group itself
        optionGroup.softDelete(null);
        optionGroupRepository.save(optionGroup);
    }

    private OptionGroup findById(Long id) {
        return optionGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm tùy chọn với ID: " + id));
    }

    private void validateSelectionRange(SelectionType selectionType, int minSelect, int maxSelect) {
        if (minSelect > maxSelect) {
            throw new InvalidParamException("Số lượng chọn tối thiểu không được lớn hơn số lượng chọn tối đa");
        }

        if (selectionType == SelectionType.REQUIRED && minSelect < 1) {
            throw new InvalidParamException("Nhóm REQUIRED phải có minSelect >= 1");
        }

        if (maxSelect < 1) {
            throw new InvalidParamException("maxSelect phải >= 1");
        }
    }
}
