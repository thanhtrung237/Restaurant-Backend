package com.group.restaurant.dtos.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    // Platform Admin không cần restaurantCode (để null), nhân viên nhà hàng cần
    private String restaurantCode;

    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    private String deviceInfo;
}
