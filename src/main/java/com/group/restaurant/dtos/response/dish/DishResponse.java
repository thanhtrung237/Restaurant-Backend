package com.group.restaurant.dtos.response.dish;

import com.group.restaurant.enums.DishStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class DishResponse {

    private Long id;
    private Long restaurantId;
    private Long branchId;
    private String branchName;
    private Long categoryId;
    private String categoryName;
    private String name;
    private BigDecimal basePrice;
    private String taxGroup;
    private String kitchenStationType;
    private String imageUrl;
    private Boolean soldByWeight;
    private DishStatus status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
