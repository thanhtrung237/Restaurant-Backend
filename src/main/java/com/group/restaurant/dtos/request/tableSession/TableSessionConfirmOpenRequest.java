package com.group.restaurant.dtos.request.tableSession;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
  Phục vụ xác nhận mở bàn → SERVING
  Áp dụng cho cả phiên WAITING_FOR_OPEN và RESERVED
 */
@Getter
@Setter
public class TableSessionConfirmOpenRequest {

    @NotNull(message = "Số khách không được để trống")
    @Min(value = 1, message = "Số khách phải >= 1")
    private Integer guestCount;

    private String note;
}
