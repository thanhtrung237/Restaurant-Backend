package com.group.restaurant.dtos.request.restaurant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestaurantAddRequest {

    @NotBlank(message = "Mã nhà hàng không được để trống")
    @Size(max = 50, message = "Mã nhà hàng không được vượt quá 50 ký tự")
    private String code;

    @NotBlank(message = "Tên nhà hàng không được để trống")
    @Size(max = 200, message = "Tên nhà hàng không được vượt quá 200 ký tự")
    private String name;

    @Size(max = 50, message = "Mã số thuế không được vượt quá 50 ký tự")
    private String taxCode;

    @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự")
    private String address;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    private String phone;

    @Size(max = 500, message = "URL logo không được vượt quá 500 ký tự")
    private String logoUrl;
}
