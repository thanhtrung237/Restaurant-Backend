package com.group.restaurant.services.impl;

import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.models.StaffNotification;
import com.group.restaurant.models.TableSession;
import com.group.restaurant.repositories.StaffNotificationRepository;
import com.group.restaurant.services.OrderNotificationService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A3 — Triển khai push STOMP (T-01: ≤ 5 giây).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNotificationServiceImpl implements OrderNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final StaffNotificationRepository notifications;

    /**
     * Gửi SAU KHI transaction commit.
     * Các service gọi notify* khi transaction còn đang mở. Nếu push ngay, FE nhận thông báo
     * rồi refetch ngay lập tức → đọc phải dữ liệu CŨ (order/trạng thái phiên chưa commit),
     * hoặc nếu transaction rollback thì khách/nhân viên vẫn nhận thông báo "ma".
     * Không có transaction thì gửi luôn.
     */
    private void send(String destination, Map<String, Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
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
            log.debug("[WS] {} payload={}", destination, payload);
        } catch (Exception e) {
            // Push thất bại không được làm hỏng nghiệp vụ chính.
            log.error("[WS] Gửi thất bại → {} payload={}", destination, payload, e);
        }
    }

    @Override
    public void notifyTableOpenRequested(TableSession session) {
        Long branchId = session.getBranch().getId();
        Long sessionId = session.getId();
        String tableName = session.getDisplayName();
        StaffNotification.Type type = StaffNotification.Type.TABLE_OPEN_REQUESTED;
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type.name());
        payload.put("tableSessionId", sessionId);
        payload.put("tableNo", tableName == null ? "" : tableName);
        persistAndSend(
                "/topic/branch/" + branchId + "/table-open-request",
                payload,
                entry(type, branchId, sessionId, tableName).build());
    }

    @Override
    public void notifyNewOrder(
            Long branchId, Long orderId, Long tableSessionId, String tableNo, Long orderedById) {
        String destination = "/topic/branch/" + branchId + "/new-order";
        Map<String, Object> payload =
                new HashMap<>(
                        Map.of(
                                "type",
                                "NEW_ORDER",
                                "orderId",
                                orderId,
                                "tableSessionId",
                                tableSessionId,
                                "tableNo",
                                tableNo != null ? tableNo : ""));
        if (orderedById != null) {
            payload.put("orderedById", orderedById);
        }
        persistAndSend(
                destination,
                payload,
                entry(StaffNotification.Type.NEW_ORDER, branchId, tableSessionId, tableNo)
                        .orderId(orderId)
                        .build());
    }

    @Override
    public void notifyItemStatusChanged(
            Long sessionId, Long orderDetailId, OrderDetailStatus newStatus) {
        String destination = "/topic/session/" + sessionId + "/item-status";
        Map<String, Object> payload =
                Map.of(
                        "type",
                        "ITEM_STATUS_CHANGED",
                        "orderDetailId",
                        orderDetailId,
                        "status",
                        newStatus.name());
        send(destination, payload);
    }

    @Override
    public void notifyMenuUpdated(Long restaurantId) {
        if (restaurantId != null)
            send("/topic/restaurant/" + restaurantId + "/menu-updated", Map.of("changed", true));
    }

    @Override
    public void notifyManagementUpdated(Long restaurantId) {
        if (restaurantId != null)
            send("/topic/restaurant/" + restaurantId + "/management-updated", Map.of("changed", true));
    }

    @Override
    public void notifyStructureUpdated(Long restaurantId) {
        notifyManagementUpdated(restaurantId);
        send("/topic/platform/structure-updated", Map.of("changed", true));
    }

    @Override
    public void notifySessionUpdated(TableSession session) {
        if (session == null) return;
        Map<String, Object> payload = Map.of("tableSessionId", session.getId());
        send("/topic/branch/" + session.getBranch().getId() + "/session-updated", payload);
        if (session.getBranch().getRestaurant() != null) {
            send(
                    "/topic/restaurant/" + session.getBranch().getRestaurant().getId()
                            + "/operations-updated",
                    Map.of("changed", true));
        }
        if (session.isTakeaway()) {
            notifyTakeawayUpdated(session);
        } else {
            send("/topic/session/" + session.getId() + "/session-updated", Map.of("changed", true));
        }
    }

    @Override
    public void notifyTakeawayUpdated(TableSession session) {
        if (session == null || !session.isTakeaway()) return;
        send(
                "/topic/branch/" + session.getBranch().getId() + "/takeaway-updated",
                Map.of("tableSessionId", session.getId()));
    }

    @Override
    public void notifyPaymentRequested(Long branchId, Long sessionId, String tableNo) {
        String destination = "/topic/branch/" + branchId + "/payment-request";
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "PAYMENT_REQUESTED");
        payload.put("tableSessionId", sessionId);
        payload.put("tableNo", tableNo != null ? tableNo : "");
        persistAndSend(
                destination,
                payload,
                entry(StaffNotification.Type.PAYMENT_REQUESTED, branchId, sessionId, tableNo)
                        .build());
    }

    @Override
    public void notifyPaymentQrOpened(TableSession session) {
        if (notifications.existsByTableSessionIdAndType(
                session.getId(), StaffNotification.Type.PAYMENT_QR_OPENED)) return;
        sendPaymentNotice(session, StaffNotification.Type.PAYMENT_QR_OPENED);
    }

    @Override
    public void notifyBankPaymentVerified(TableSession session) {
        sendPaymentNotice(session, StaffNotification.Type.BANK_PAYMENT_VERIFIED);
    }

    @Override
    public void notifyDiscountApprovalRequested(TableSession session) {
        sendPaymentNotice(session, StaffNotification.Type.DISCOUNT_APPROVAL);
    }

    private void sendPaymentNotice(TableSession session, StaffNotification.Type type) {
        Long branchId = session.getBranch().getId();
        String tableName = session.getDisplayName();
        Map<String, Object> payload =
                new HashMap<>(
                        Map.of(
                                "type",
                                type.name(),
                                "tableSessionId",
                                session.getId(),
                                "tableNo",
                                tableName));
        persistAndSend(
                "/topic/branch/" + branchId + "/payment-request",
                payload,
                entry(type, branchId, session.getId(), tableName).build());
    }

    @Override
    public void notifyStaffCall(
            Long branchId, Long sessionId, Long orderDetailId, String tableNo, String dishName) {
        String destination = "/topic/branch/" + branchId + "/staff-call";
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "STAFF_CALL");
        payload.put("tableSessionId", sessionId);
        payload.put("orderDetailId", orderDetailId);
        payload.put("tableNo", tableNo != null ? tableNo : "");
        payload.put("dishName", dishName != null ? dishName : "");
        persistAndSend(
                destination,
                payload,
                entry(StaffNotification.Type.STAFF_CALL, branchId, sessionId, tableNo)
                        .orderDetailId(orderDetailId)
                        .dishName(dishName)
                        .build());
    }

    private StaffNotification.StaffNotificationBuilder entry(
            StaffNotification.Type type, Long branchId, Long sessionId, String tableNo) {
        return StaffNotification.builder()
                .type(type)
                .branchId(branchId)
                .tableSessionId(sessionId)
                .tableName(tableNo == null ? "" : tableNo)
                .createdAt(Instant.now());
    }

    private void persistAndSend(
            String destination, Map<String, Object> payload, StaffNotification notification) {
        payload.put("notificationId", notifications.save(notification).getId());
        send(destination, payload);
    }
}
