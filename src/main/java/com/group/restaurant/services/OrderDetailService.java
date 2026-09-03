package com.group.restaurant.services;

import com.group.restaurant.dtos.request.orderDetail.OrderDetailAddRequest;
import com.group.restaurant.dtos.request.orderDetail.OrderDetailUpdateRequest;
import com.group.restaurant.dtos.response.orderDetail.OrderDetailResponse;
import com.group.restaurant.enums.OrderDetailStatus;

import java.util.List;

public interface OrderDetailService {

    OrderDetailResponse addOrderDetail(Long orderId, OrderDetailAddRequest request);

    OrderDetailResponse updateOrderDetail(Long id, OrderDetailUpdateRequest request);

    OrderDetailResponse getOrderDetailById(Long id);

    List<OrderDetailResponse> getOrderDetailsByOrder(Long orderId);

    List<OrderDetailResponse> getOrderDetailsByTableSession(Long tableSessionId, OrderDetailStatus status);

    void changeOrderDetailStatus(Long id, OrderDetailStatus status);

    void deleteOrderDetail(Long id);
}
