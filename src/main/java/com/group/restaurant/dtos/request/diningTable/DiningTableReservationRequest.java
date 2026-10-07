package com.group.restaurant.dtos.request.diningTable;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DiningTableReservationRequest {

    @NotNull(message = "Vui lòng chọn chi nhánh")
    private Long branchId;

    @NotBlank(message = "Tên khách hàng không được để trống")
    private String customerName;

    @NotBlank(message = "Số điện thoại khách hàng không được để trống")
    private String customerPhone;

    @NotNull(message = "Số lượng khách không được để trống")
    @Min(value = 1, message = "Số lượng khách phải >= 1")
    private Integer guestCount;

    @NotNull(message = "Thời gian hẹn không được để trống")
    @Future(message = "Thời gian đặt bàn phải ở tương lai")
    private LocalDateTime reservationTime;

    private String note;
}