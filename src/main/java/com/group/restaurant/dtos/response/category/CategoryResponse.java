package com.group.restaurant.dtos.response.category;

import com.group.restaurant.enums.CategoryStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class CategoryResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String name;
    private Integer displayOrder;
    private CategoryStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
