package com.group.restaurant.dtos.request.area;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AreaAddRequest {

    @NotNull(message = "Branch ID không được để trống")
    private Long branchId;

    @NotBlank(message = "Tên khu vực không được để trống")
    @Size(max = 100, message = "Tên khu vực không được vượt quá 100 ký tự")
    private String name;
}
