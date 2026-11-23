package com.group.restaurant.services;

import com.group.restaurant.dtos.request.orderDetail.OrderDetailAddRequest;
import com.group.restaurant.dtos.request.orderDetail.OrderDetailCancelRequest;
import com.group.restaurant.dtos.request.orderDetail.OrderDetailConfirmWeightRequest;
import com.group.restaurant.dtos.request.orderDetail.OrderDetailServeRequest;
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

    // Phục vụ xác nhận đã mang món ra bàn: PREPARING → SERVED
    OrderDetailResponse markAsServed(Long id, OrderDetailServeRequest request);

    // Phục vụ nhập số cân: PENDING_WEIGHT → PREPARING 
    OrderDetailResponse confirmWeight(Long id, OrderDetailConfirmWeightRequest request);

    // Hủy dòng món sau khi vào bếp với lý do 
    OrderDetailResponse cancelAfterKitchen(Long id, OrderDetailCancelRequest request);

    // Hủy dòng món trước bếp (PENDING / PENDING_WEIGHT → CANCELLED)
    OrderDetailResponse cancelBeforeKitchen(Long id);

    OrderDetailResponse removeFromCheckout(Long sessionId, Long detailId, String reason);

    void changeOrderDetailStatus(Long id, OrderDetailStatus status);

    // Xác nhận hàng loạt món chờ chế biến (PENDING → PREPARING)
    List<OrderDetailResponse> batchConfirm(com.group.restaurant.dtos.request.orderDetail.OrderDetailBatchConfirmRequest request);

    void deleteOrderDetail(Long id);
}
