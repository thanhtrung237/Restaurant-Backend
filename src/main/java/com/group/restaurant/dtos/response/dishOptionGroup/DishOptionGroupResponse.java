package com.group.restaurant.dtos.response.dishOptionGroup;

import com.group.restaurant.enums.SelectionType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DishOptionGroupResponse {

    private Long id;
    private Long dishId;
    private String dishName;
    private Long optionGroupId;
    private String optionGroupName;
    private SelectionType selectionType;
    private int minSelect;
    private int maxSelect;
}
