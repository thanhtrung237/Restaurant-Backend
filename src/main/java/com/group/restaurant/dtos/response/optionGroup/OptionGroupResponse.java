package com.group.restaurant.dtos.response.optionGroup;

import com.group.restaurant.enums.SelectionType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class OptionGroupResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String name;
    private SelectionType selectionType;
    private int minSelect;
    private int maxSelect;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
