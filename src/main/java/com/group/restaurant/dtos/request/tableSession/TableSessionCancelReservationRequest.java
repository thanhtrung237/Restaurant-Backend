package com.group.restaurant.dtos.request.tableSession;

import lombok.Getter;
import lombok.Setter;

/**
  Hủy đặt bàn trước (RESERVED → CANCELLED)
 */
@Getter
@Setter
public class TableSessionCancelReservationRequest {

    private String reason;
}
