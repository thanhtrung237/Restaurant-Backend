package com.group.restaurant.dtos.request.orderDetail;

import com.group.restaurant.enums.CancelReason;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
  Hủy dòng món sau khi đã vào bếp 
  - RESTAURANT_ERROR  → không tính tiền, cần QL duyệt
  - CUSTOMER_CHANGED_MIND → vẫn tính tiền, không cần duyệt
 */
@Getter
@Setter
public class OrderDetailCancelRequest {

    @NotNull(message = "Lý do hủy không được để trống")
    private CancelReason cancelReason;
}
