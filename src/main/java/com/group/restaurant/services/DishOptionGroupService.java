package com.group.restaurant.services;

import com.group.restaurant.dtos.request.dishOptionGroup.DishOptionGroupAddRequest;
import com.group.restaurant.dtos.response.dishOptionGroup.DishOptionGroupResponse;
import java.util.List;

public interface DishOptionGroupService {

    DishOptionGroupResponse linkDishToOptionGroup(DishOptionGroupAddRequest request);

    DishOptionGroupResponse getDishOptionGroupById(Long id);

    List<DishOptionGroupResponse> getDishOptionGroupsByDish(Long dishId);

    List<DishOptionGroupResponse> getDishOptionGroupsByOptionGroup(Long optionGroupId);

    void unlinkDishFromOptionGroup(Long id);
}
