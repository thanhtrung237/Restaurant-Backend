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
    @Size(max = 20, message = "Mã nhân viên không được vượt quá 20 ký tự")
    private String employeeCode;
    // employeeCode là mã cố định, không cho phép update (giống restaurant.code và
    // branch.code)

    @Email(message = "Email không hợp lệ")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;
}
