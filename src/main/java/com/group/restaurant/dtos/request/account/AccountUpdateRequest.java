package com.group.restaurant.dtos.request.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountUpdateRequest {

    @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
    private String fullName;
    // QĐ-03: mã nhân viên là mã cố định do hệ thống sinh — KHÔNG có trong DTO cập nhật
    // (giống restaurant.code và branch.code).

    @Email(message = "Email không hợp lệ")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;
}
