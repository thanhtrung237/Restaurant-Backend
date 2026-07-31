package com.group.restaurant.dtos.request.order;

import com.group.restaurant.dtos.request.orderDetail.OrderDetailAddRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OrderAddRequest {

    @NotNull(message = "Table Session ID không được để trống")
    private Long tableSessionId;

    private Long orderedById;

    private String note;

    @NotEmpty(message = "Đơn hàng phải có ít nhất 1 món")
    @Valid
    private List<OrderDetailAddRequest> items;
}
