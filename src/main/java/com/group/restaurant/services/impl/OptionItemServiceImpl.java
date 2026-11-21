package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.optionItem.OptionItemAddRequest;
import com.group.restaurant.dtos.request.optionItem.OptionItemUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.optionItem.OptionItemResponse;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.OptionItemMapper;
import com.group.restaurant.models.OptionGroup;
import com.group.restaurant.models.OptionItem;
import com.group.restaurant.repositories.OptionGroupRepository;
import com.group.restaurant.repositories.OptionItemRepository;
import com.group.restaurant.services.OptionItemService;
import com.group.restaurant.utils.TenantGuard;
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
public class OptionItemServiceImpl implements OptionItemService {

    private final OptionItemRepository optionItemRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final OptionItemMapper optionItemMapper;
    private final TenantGuard tenantGuard;

    @Override
    public OptionItemResponse createOptionItem(OptionItemAddRequest request) {
        OptionGroup optionGroup = optionGroupRepository.findById(request.getOptionGroupId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhóm tùy chọn với ID: " + request.getOptionGroupId()));
        tenantGuard.assertRestaurant(optionGroup.getRestaurant().getId());

        OptionItem optionItem = optionItemMapper.toEntity(request);
        optionItem.setOptionGroup(optionGroup);

        OptionItem saved = optionItemRepository.save(optionItem);
        return optionItemMapper.toResponse(saved);
    }

    @Override
    public OptionItemResponse updateOptionItem(Long id, OptionItemUpdateRequest request) {
        OptionItem optionItem = findById(id);

        optionItemMapper.updateEntity(optionItem, request);
        OptionItem saved = optionItemRepository.save(optionItem);
        return optionItemMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OptionItemResponse getOptionItemById(Long id) {
        OptionItem optionItem = findById(id);
        return optionItemMapper.toResponse(optionItem);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OptionItemResponse> getOptionItemsByGroup(Long optionGroupId) {
        List<OptionItem> optionItems = optionItemRepository.findByOptionGroupId(optionGroupId);
        return optionItems.stream()
                .map(optionItemMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OptionItemResponse> getAllOptionItems(Long optionGroupId, String keyword, Pageable pageable) {
        // optionGroupId đến từ client — nhóm tùy chọn phải thuộc nhà hàng của người đăng nhập.
        if (optionGroupId != null) {
            optionGroupRepository.findById(optionGroupId).ifPresent(g ->
                    tenantGuard.assertRestaurant(
                            g.getRestaurant() != null ? g.getRestaurant().getId() : null));
        }
        Page<OptionItem> page;
        
        if (optionGroupId != null && keyword != null && !keyword.trim().isEmpty()) {
            page = optionItemRepository.findByOptionGroupIdAndNameContainingIgnoreCase(
                    optionGroupId, keyword.trim(), pageable);
        } else if (optionGroupId != null) {
            page = optionItemRepository.findByOptionGroupId(optionGroupId, pageable);
        } else if (keyword != null && !keyword.trim().isEmpty()) {
            page = optionItemRepository.findByNameContainingIgnoreCase(keyword.trim(), pageable);
        } else {
            page = optionItemRepository.findAll(pageable);
        }

        List<OptionItemResponse> content = page.getContent().stream()
                .map(optionItemMapper::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<OptionItemResponse>builder()
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
    public void deleteOptionItem(Long id) {
        OptionItem optionItem = findById(id);
        
        // Soft delete
        optionItem.softDelete(null);
        optionItemRepository.save(optionItem);
    }

    private OptionItem findById(Long id) {
        OptionItem item = optionItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lựa chọn với ID: " + id));
        tenantGuard.assertRestaurant(item.getOptionGroup().getRestaurant().getId());
        return item;
    }
}
