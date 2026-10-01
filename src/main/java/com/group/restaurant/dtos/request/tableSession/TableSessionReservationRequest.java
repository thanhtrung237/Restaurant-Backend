package com.group.restaurant.dtos.request.tableSession;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * FR-TBL-03 — Đặt bàn trước (RESERVED).
 * Chỉ Phục vụ và QL chi nhánh gọi endpoint này.
 */
@Getter
@Setter
public class TableSessionReservationRequest {

    @NotNull(message = "Table ID không được để trống")
    private Long tableId;

    @NotBlank(message = "Tên khách không được để trống (BR-29)")
    private String reservationName;

    @NotBlank(message = "Số điện thoại khách không được để trống (BR-29)")
    private String reservationPhone;

    @NotNull(message = "Thời gian dự kiến đến không được để trống (BR-29)")
    private LocalDateTime reservationTime;

    private Integer guestCount;
    private String reservationNote;
}
