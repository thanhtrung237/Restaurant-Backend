package com.group.restaurant.services.impl;

import com.group.restaurant.services.OrderNotificationService;
import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.DiningTable;
import com.group.restaurant.models.TableSession;
import com.group.restaurant.repositories.DiningTableRepository;
import com.group.restaurant.repositories.TableSessionRepository;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job nền xử lý tự động hủy phiên bàn theo BR-27 và BR-29.
 *
 * BR-27: Phiên CHỜ MỞ BÀN không được xác nhận trong
 *        {@code branch.pendingConfirmTimeoutMinutes} (mặc định 10 phút) → tự động HỦY.
 *
 * BR-29: Phiên ĐẶT TRƯỚC quá {@code branch.reservationHoldMinutes} sau giờ hẹn
 *        (mặc định 15 phút) mà chưa được xác nhận khách đến → tự động HỦY, giải phóng bàn.
 *
 * T-08: Cả hai job chạy mỗi 1 phút. Độ trễ thực tế ≤ 1 phút là chấp nhận được.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TableSessionScheduler {

    private final TableSessionRepository tableSessionRepository;
    private final OrderNotificationService orderNotificationService;
    private final DiningTableRepository diningTableRepository;
    private final AuditLogService auditLogService;

    private static final int DEFAULT_PENDING_TIMEOUT_MINUTES = 10;
    private static final int DEFAULT_RESERVATION_HOLD_MINUTES = 15;

    /**
     * BR-27: Tự động hủy phiên CHỜ MỞ BÀN hết hạn.
     * Chạy mỗi 60 giây.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cancelExpiredWaitingForOpenSessions() {
        List<TableSession> sessions =
                tableSessionRepository.findByStatus(TableSessionStatus.WAITING_FOR_OPEN);
        LocalDateTime now = LocalDateTime.now();
        int cancelled = 0;

        for (TableSession session : sessions) {
            int timeoutMinutes = getTimeoutMinutes(session);
            LocalDateTime createdAt = session.getCreatedAt();
            if (createdAt == null) continue;

            if (createdAt.plusMinutes(timeoutMinutes).isBefore(now)) {
                cancelAndFreeTable(
                        session,
                        "Phiên CHỜ MỞ BÀN hết hạn tự động sau " + timeoutMinutes + " phút (BR-27)");
                cancelled++;
            }
        }

        if (cancelled > 0) {
            log.info("[BR-27] Đã tự động hủy {} phiên CHỜ MỞ BÀN hết hạn", cancelled);
        }
    }

    /**
     * BR-29: Tự động hủy phiên ĐẶT TRƯỚC quá giờ giữ chỗ.
     * Chạy mỗi 60 giây.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cancelExpiredReservedSessions() {
        List<TableSession> sessions =
                tableSessionRepository.findByStatus(TableSessionStatus.RESERVED);
        LocalDateTime now = LocalDateTime.now();
        int cancelled = 0;

        for (TableSession session : sessions) {
            if (session.getReservationTime() == null) continue;

            int holdMinutes = getHoldMinutes(session);
            LocalDateTime deadline = session.getReservationTime().plusMinutes(holdMinutes);

            if (deadline.isBefore(now)) {
                cancelAndFreeTable(
                        session,
                        "Phiên ĐẶT TRƯỚC tự động hủy: khách "
                                + session.getReservationName()
                                + " không đến sau "
                                + holdMinutes
                                + " phút kể từ giờ hẹn "
                                + session.getReservationTime()
                                + " (BR-29)");
                cancelled++;
            }
        }

        if (cancelled > 0) {
            log.info("[BR-29] Đã tự động hủy {} phiên ĐẶT TRƯỚC quá giờ giữ chỗ", cancelled);
        }
    }

    /** Hủy phiên, giải phóng bàn, ghi nhật ký. Dùng chung cho BR-27 và BR-29. */
    private void cancelAndFreeTable(TableSession session, String reason) {
        TableSessionStatus previousStatus = session.getStatus();
        session.setStatus(TableSessionStatus.CANCELLED);
        session.setClosedAt(LocalDateTime.now());
        session.setNote(reason);
        tableSessionRepository.save(session);
        orderNotificationService.notifySessionUpdated(session);

        DiningTable table = session.getTable();
        if (table != null) {
            table.setStatus(DiningTableStatus.AVAILABLE);
            diningTableRepository.save(table);
        }

        try {
            Long branchId = session.getBranch() != null ? session.getBranch().getId() : null;
            auditLogService.record(
                    AuditEvent.builder()
                            .entityType("TableSession")
                            .entityId(session.getId())
                            .actionType(AuditAction.CANCEL)
                            .branchId(branchId)
                            .valueBefore("status=" + previousStatus)
                            .valueAfter("status=" + TableSessionStatus.CANCELLED)
                            .reason(reason)
                            .build());
        } catch (Exception e) {
            // Không để lỗi ghi log làm rollback transaction hủy phiên
            log.warn(
                    "[Scheduler] Ghi audit log thất bại cho session {}: {}",
                    session.getId(),
                    e.getMessage());
        }
    }

    private int getTimeoutMinutes(TableSession session) {
        Branch branch = session.getBranch();
        if (branch != null && branch.getPendingConfirmTimeoutMinutes() != null) {
            return branch.getPendingConfirmTimeoutMinutes();
        }
        return DEFAULT_PENDING_TIMEOUT_MINUTES;
    }

    private int getHoldMinutes(TableSession session) {
        Branch branch = session.getBranch();
        if (branch != null && branch.getReservationHoldMinutes() != null) {
            return branch.getReservationHoldMinutes();
        }
        return DEFAULT_RESERVATION_HOLD_MINUTES;
    }
}
