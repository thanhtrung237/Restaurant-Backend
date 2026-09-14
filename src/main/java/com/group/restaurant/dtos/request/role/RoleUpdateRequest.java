package com.group.restaurant.dtos.request.role;

import com.group.restaurant.enums.RoleScope;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class RoleUpdateRequest {

    @Size(max = 50, message = "Tên vai trò không được vượt quá 50 ký tự")
    private String name;

    private RoleScope scope;
}
