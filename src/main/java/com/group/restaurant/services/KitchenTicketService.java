package com.group.restaurant.services;

import com.group.restaurant.models.OrderDetail;

import java.util.List;

/**
 * FR-SRV-07: Phiếu bếp — tạo khi dòng món chuyển PREPARING, gửi theo khu chế biến.
 */
public interface KitchenTicketService {

    /** In phiếu bếp cho các dòng món vừa chuyển PREPARING trong một order */
    void printKitchenTickets(Long orderId, List<OrderDetail> preparingDetails);

    /** In phiếu hủy gửi bếp khi hủy dòng món sau bếp (BR-24) */
    void printCancelTicket(OrderDetail cancelledDetail);
}
