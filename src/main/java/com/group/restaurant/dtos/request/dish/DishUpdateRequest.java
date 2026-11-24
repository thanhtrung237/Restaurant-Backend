package com.group.restaurant.dtos.request.dish;

import com.group.restaurant.enums.DishStatus;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DishUpdateRequest {

    private Long categoryId;

    @Size(max = 200, message = "Tên món không được vượt quá 200 ký tự")
    private String name;

    private BigDecimal basePrice;

    @Size(max = 50, message = "Tax group không được vượt quá 50 ký tự")
    private String taxGroup;

    @Size(max = 50, message = "Kitchen station type không được vượt quá 50 ký tự")
    private String kitchenStationType;

    @Size(max = 500, message = "Image URL không được vượt quá 500 ký tự")
    private String imageUrl;

    private Boolean soldByWeight;

    private DishStatus status;

    private String description;
}
