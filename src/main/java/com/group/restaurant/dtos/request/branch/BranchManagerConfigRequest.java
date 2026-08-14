package com.group.restaurant.dtos.request.branch;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/** Quản lý chi nhánh có thể sửa:
 giờ mở/đóng, giờ bắt đầu ngày kinh doanh, tài khoản ngân hàng, timeout CHỜ MỞ BÀN, timeout ĐẶT TRƯỚC */
@Getter
@Setter
public class BranchManagerConfigRequest {

    private LocalTime openingTime;

    private LocalTime closingTime;

    private LocalTime businessDayStartTime;

    @Size(max = 50, message = "Số tài khoản không được vượt quá 50 ký tự")
    private String bankAccountNo;

    @Size(max = 200, message = "Tên ngân hàng không được vượt quá 200 ký tự")
    private String bankName;

    @Min(value = 1, message = "Timeout CHỜ MỞ BÀN phải >= 1 phút")
    private Integer pendingConfirmTimeoutMinutes;

    @Min(value = 1, message = "Timeout ĐẶT TRƯỚC phải >= 1 phút")
    private Integer reservationHoldMinutes;
}
