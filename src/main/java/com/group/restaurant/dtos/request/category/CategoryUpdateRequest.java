package com.group.restaurant.dtos.request.category;

import com.group.restaurant.enums.CategoryStatus;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryUpdateRequest {

    @Size(max = 200, message = "Tên danh mục không được vượt quá 200 ký tự")
    private String name;

    private Integer displayOrder;

    private CategoryStatus status;
}
