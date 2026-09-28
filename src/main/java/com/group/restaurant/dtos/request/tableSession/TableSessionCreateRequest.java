package com.group.restaurant.dtos.request.tableSession;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 Quét QR → tạo phiên CHỜ MỞ BÀN (WAITING_FOR_OPEN)
 hoặc Đặt bàn trước → tạo phiên ĐẶT TRƯỚC (RESERVED)
 */
@Getter
@Setter
public class TableSessionCreateRequest {

    @NotNull(message = "Table ID không được để trống")
    private Long tableId;

    /** Số khách khách tự chọn khi quét QR (luồng WAITING_FOR_OPEN, KHÔNG phải đặt trước). */
    private Integer guestCount;

    private String reservationName;
    private String reservationPhone;
    private java.time.LocalDateTime reservationTime;
    private Integer reservedGuestCount;
    private String reservationNote;
}