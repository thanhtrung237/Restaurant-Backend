package com.group.restaurant.services.impl;

import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.services.OrderNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;

/**
 * A3 — Triển khai push STOMP (T-01: ≤ 5 giây).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNotificationServiceImpl implements OrderNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Gửi SAU KHI transaction commit.
     * Các service gọi notify* khi transaction còn đang mở. Nếu push ngay, FE nhận thông báo
     * rồi refetch ngay lập tức → đọc phải dữ liệu CŨ (order/trạng thái phiên chưa commit),
     * hoặc nếu transaction rollback thì khách/nhân viên vẫn nhận thông báo "ma".
     * Không có transaction thì gửi luôn.
     */
    private void send(String destination, Map<String, Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(destination, payload);
                }
            });
        } else {
            doSend(destination, payload);
        }
    }

    private void doSend(String destination, Map<String, Object> payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
            log.info("[WS] {} payload={}", destination, payload);
        } catch (Exception e) {
            // Push thất bại không được làm hỏng nghiệp vụ chính.
            log.error("[WS] Gửi thất bại → {} payload={}", destination, payload, e);
        }
    }

    @Override
    public void notifyNewOrder(Long branchId, Long orderId, Long tableSessionId, String tableNo) {
        String destination = "/topic/branch/" + branchId + "/new-order";
        Map<String, Object> payload = Map.of(
                "type", "NEW_ORDER",
                "orderId", orderId,
                "tableSessionId", tableSessionId,
                "tableNo", tableNo != null ? tableNo : ""
        );
        send(destination, payload);
    }

    @Override
    public void notifyItemStatusChanged(Long sessionId, Long orderDetailId, OrderDetailStatus newStatus) {
        String destination = "/topic/session/" + sessionId + "/item-status";
        Map<String, Object> payload = Map.of(
                "type", "ITEM_STATUS_CHANGED",
                "orderDetailId", orderDetailId,
                "status", newStatus.name()
        );
        send(destination, payload);
    }

    @Override
    public void notifyPaymentRequested(Long branchId, Long sessionId, String tableNo) {
        String destination = "/topic/branch/" + branchId + "/payment-request";
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "PAYMENT_REQUESTED");
        payload.put("tableSessionId", sessionId);
        payload.put("tableNo", tableNo != null ? tableNo : "");
        send(destination, payload);
    }

    @Override
    public void notifyStaffCall(Long branchId, Long sessionId, Long orderDetailId, String tableNo, String dishName) {
        String destination = "/topic/branch/" + branchId + "/staff-call";
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "STAFF_CALL");
        payload.put("tableSessionId", sessionId);
        payload.put("orderDetailId", orderDetailId);
        payload.put("tableNo", tableNo != null ? tableNo : "");
        payload.put("dishName", dishName != null ? dishName : "");
        send(destination, payload);
    }
}