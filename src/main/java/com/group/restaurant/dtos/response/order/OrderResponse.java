package com.group.restaurant.dtos.response.order;

import com.group.restaurant.dtos.response.orderDetail.OrderDetailResponse;
import com.group.restaurant.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class OrderResponse {

    private Long id;
    private Long tableSessionId;
    private Long orderedById;
    private String orderedByName;
    private String note;
    private OrderStatus status;
    private List<OrderDetailResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
