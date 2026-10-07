package com.group.restaurant.services;

import com.group.restaurant.enums.OrderDetailStatus;

/**
 * A3 — Push thông báo real-time qua STOMP WebSocket (T-01).
 */
public interface OrderNotificationService {

    /** Push order mới đến tất cả subscriber theo chi nhánh */
    void notifyNewOrder(Long branchId, Long orderId, Long tableSessionId, String tableNo);

    /** Push khi dòng món đổi trạng thái */
    void notifyItemStatusChanged(Long sessionId, Long orderDetailId, OrderDetailStatus newStatus);

    /** Push khi khách bấm "Yêu cầu tính tiền" (FR-CUS-10) */
    void notifyPaymentRequested(Long branchId, Long sessionId, String tableNo);

    /** Push khi khách bấm "Báo nhân viên" cho món đã vào bếp (FR-CUS-07) */
    void notifyStaffCall(Long branchId, Long sessionId, Long orderDetailId, String tableNo, String dishName);
}
