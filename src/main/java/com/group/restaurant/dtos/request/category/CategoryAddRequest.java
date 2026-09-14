package com.group.restaurant.dtos.request.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryAddRequest {

    @NotNull(message = "Restaurant ID không được để trống")
    private Long restaurantId;

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 200, message = "Tên danh mục không được vượt quá 200 ký tự")
    private String name;

    private Integer displayOrder;
}
