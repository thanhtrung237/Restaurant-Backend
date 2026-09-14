package com.group.restaurant.dtos.request.dish;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DishAddRequest {

    @NotNull(message = "Restaurant ID không được để trống")
    private Long restaurantId;

    // null = món gốc cấp nhà hàng; có giá trị = món riêng chi nhánh (FR-MNU-04)
    private Long branchId;

    @NotNull(message = "Category ID không được để trống")
    private Long categoryId;

    @NotBlank(message = "Tên món không được để trống")
    @Size(max = 200, message = "Tên món không được vượt quá 200 ký tự")
    private String name;

    @NotNull(message = "Giá gốc không được để trống")
    private BigDecimal basePrice;

    @Size(max = 50, message = "Tax group không được vượt quá 50 ký tự")
    private String taxGroup;

    @Size(max = 50, message = "Kitchen station type không được vượt quá 50 ký tự")
    private String kitchenStationType;

    @Size(max = 500, message = "Image URL không được vượt quá 500 ký tự")
    private String imageUrl;

    private Boolean soldByWeight;

    private String description;
}
