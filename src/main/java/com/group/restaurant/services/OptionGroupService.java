package com.group.restaurant.services;

import com.group.restaurant.dtos.request.optionGroup.OptionGroupAddRequest;
import com.group.restaurant.dtos.request.optionGroup.OptionGroupUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.optionGroup.OptionGroupResponse;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface OptionGroupService {

    OptionGroupResponse createOptionGroup(OptionGroupAddRequest request);

    OptionGroupResponse updateOptionGroup(Long id, OptionGroupUpdateRequest request);

    OptionGroupResponse getOptionGroupById(Long id);

    List<OptionGroupResponse> getOptionGroupsByRestaurant(Long restaurantId);

    PageResponse<OptionGroupResponse> getAllOptionGroups(
            Long restaurantId, String keyword, Pageable pageable);

    void deleteOptionGroup(Long id);
}
