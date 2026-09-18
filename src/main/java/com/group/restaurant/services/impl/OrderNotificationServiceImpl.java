package com.group.restaurant.services.impl;

import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.services.OrderNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * A3 — Triển khai push STOMP (T-01: ≤ 5 giây).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNotificationServiceImpl implements OrderNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void notifyNewOrder(Long branchId, Long orderId, Long tableSessionId, String tableNo) {
        String destination = "/topic/branch/" + branchId + "/new-order";
        Map<String, Object> payload = Map.of(
                "orderId", orderId,
                "tableSessionId", tableSessionId,
                "tableNo", tableNo != null ? tableNo : ""
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("[WS] new-order → {} payload={}", destination, payload);
    }

    @Override
    public void notifyItemStatusChanged(Long sessionId, Long orderDetailId, OrderDetailStatus newStatus) {
        String destination = "/topic/session/" + sessionId + "/item-status";
        Map<String, Object> payload = Map.of(
                "orderDetailId", orderDetailId,
                "status", newStatus.name()
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("[WS] item-status → {} payload={}", destination, payload);
    }
}
