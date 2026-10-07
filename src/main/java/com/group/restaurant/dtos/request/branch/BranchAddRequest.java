package com.group.restaurant.dtos.request.branch;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;


@Getter
@Setter
public class BranchAddRequest {

    @NotNull(message = "Restaurant ID không được để trống")
    private Long restaurantId;

    @NotBlank(message = "Mã chi nhánh không được để trống")
    @Size(max = 50, message = "Mã chi nhánh không được vượt quá 50 ký tự")
    private String code;

    @NotBlank(message = "Tên chi nhánh không được để trống")
    @Size(max = 200, message = "Tên chi nhánh không được vượt quá 200 ký tự")
    private String name;

    @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự")
    private String address;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    private String phone;

    private LocalTime openingTime;

    private LocalTime closingTime;

  
    private LocalTime businessDayStartTime;


    @Size(max = 50, message = "Mã số thuế không được vượt quá 50 ký tự")
    private String taxCode;

    @Size(max = 50, message = "Số tài khoản không được vượt quá 50 ký tự")
    private String bankAccountNo;

    @DecimalMin(value = "0.00", message = "Phí dịch vụ phải >= 0")
    @DecimalMax(value = "999.99", message = "Phí dịch vụ phải <= 999.99")
    private BigDecimal serviceFeeRate;

   
    @DecimalMin(value = "0.00", message = "Thuế VAT phải >= 0")
    @DecimalMax(value = "999.99", message = "Thuế VAT phải <= 999.99")
    private BigDecimal vatRate;
}
