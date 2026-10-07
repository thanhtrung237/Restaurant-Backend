package com.group.restaurant.services;

import com.group.restaurant.dtos.request.optionItem.OptionItemAddRequest;
import com.group.restaurant.dtos.request.optionItem.OptionItemUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.optionItem.OptionItemResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OptionItemService {

    OptionItemResponse createOptionItem(OptionItemAddRequest request);

    OptionItemResponse updateOptionItem(Long id, OptionItemUpdateRequest request);

    OptionItemResponse getOptionItemById(Long id);

    List<OptionItemResponse> getOptionItemsByGroup(Long optionGroupId);

    PageResponse<OptionItemResponse> getAllOptionItems(
            Long optionGroupId,
            String keyword,
            Pageable pageable
    );

    void deleteOptionItem(Long id);
}
