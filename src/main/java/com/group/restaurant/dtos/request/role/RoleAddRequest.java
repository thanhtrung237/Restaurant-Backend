package com.group.restaurant.dtos.request.role;

import com.group.restaurant.enums.RoleScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleAddRequest {

    @NotNull(message = "Restaurant ID không được để trống")
    private Long restaurantId;

    @NotBlank(message = "Tên vai trò không được để trống")
    @Size(max = 50, message = "Tên vai trò không được vượt quá 50 ký tự")
    private String name;

    @NotBlank(message = "Mã vai trò không được để trống")
    @Size(max = 50, message = "Mã vai trò không được vượt quá 50 ký tự")
    private String code;

    @NotNull(message = "Phạm vi vai trò không được để trống")
    private RoleScope scope;
}
