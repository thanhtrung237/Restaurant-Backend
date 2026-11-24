package com.group.restaurant.services;

import com.group.restaurant.dtos.request.order.OrderAddRequest;
import com.group.restaurant.dtos.request.order.OrderUpdateRequest;
import com.group.restaurant.dtos.response.order.OrderResponse;
import com.group.restaurant.enums.OrderStatus;
import java.util.List;

public interface OrderService {

    OrderResponse createOrder(OrderAddRequest request);

    // Phục vụ xác nhận order → tất cả dòng PENDING chuyển PREPARING
    OrderResponse confirmOrder(Long id);

    OrderResponse updateOrder(Long id, OrderUpdateRequest request);

    OrderResponse getOrderById(Long id);

    List<OrderResponse> getOrdersByTableSession(Long tableSessionId, OrderStatus status);

    List<OrderResponse> getOrdersByBranch(Long branchId, OrderStatus status);

    void changeOrderStatus(Long id, OrderStatus status);

    void cancelOrder(Long id);

    void deleteOrder(Long id);
}
