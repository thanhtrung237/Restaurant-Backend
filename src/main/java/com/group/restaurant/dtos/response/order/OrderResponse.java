package com.group.restaurant.dtos.response.order;

import com.group.restaurant.dtos.response.orderDetail.OrderDetailResponse;
import com.group.restaurant.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

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
