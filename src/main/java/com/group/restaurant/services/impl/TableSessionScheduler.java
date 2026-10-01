package com.group.restaurant.services.impl;

import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.TableSession;
import com.group.restaurant.repositories.DiningTableRepository;
import com.group.restaurant.repositories.TableSessionRepository;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TableSessionScheduler {

    private final TableSessionRepository tableSessionRepository;
    private final DiningTableRepository diningTableRepository;
    private final AuditLogService auditLogService;

    /**
        Tự hủy phiên WAITING_FOR_OPEN quá thời gian xác nhận (mặc định 10 phút)
        Chạy mỗi 1 phút
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cancelExpiredWaitingForOpenSessions() {
        List<TableSession> sessions = tableSessionRepository
                .findByStatus(TableSessionStatus.WAITING_FOR_OPEN);

        for (TableSession session : sessions) {
            Branch branch = session.getTable().getBranch();
            int timeoutMinutes = branch.getPendingConfirmTimeoutMinutes() != null
                    ? branch.getPendingConfirmTimeoutMinutes()
                    : 10;

            LocalDateTime expireAt = session.getCreatedAt().plusMinutes(timeoutMinutes);
            if (LocalDateTime.now().isAfter(expireAt)) {
                session.setStatus(TableSessionStatus.CANCELLED);
                session.setClosedAt(LocalDateTime.now());
                tableSessionRepository.save(session);

                session.getTable().setStatus(DiningTableStatus.AVAILABLE);
                diningTableRepository.save(session.getTable());

                log.info("Auto-cancelled WAITING_FOR_OPEN session id={} (timeout {} min)",
                        session.getId(), timeoutMinutes);

                // BR-12: ghi nhật ký tự động hủy phiên
                try {
                    auditLogService.record(AuditEvent.builder()
                            .entityType("TableSession")
                            .entityId(session.getId())
                            .actionType(AuditAction.CANCEL)
                            .branchId(branch.getId())
                            .valueBefore("status=" + TableSessionStatus.WAITING_FOR_OPEN)
                            .valueAfter("status=" + TableSessionStatus.CANCELLED)
                            .reason("Tự động hủy do quá " + timeoutMinutes + " phút không xác nhận (BR-27)")
                            .build());
                } catch (Exception e) {
                    log.error("Failed to write audit log for auto-cancel session id={}", session.getId(), e);
                }
            }
        }
    }

    /**
        Tự hủy phiên RESERVED quá giờ giữ chỗ (mặc định 15 phút sau reservationTime)
        Chạy mỗi 1 phút
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cancelExpiredReservations() {
        List<TableSession> sessions = tableSessionRepository
                .findByStatus(TableSessionStatus.RESERVED);

        for (TableSession session : sessions) {
            if (session.getReservationTime() == null) continue;

            Branch branch = session.getTable().getBranch();
            int holdMinutes = branch.getReservationHoldMinutes() != null
                    ? branch.getReservationHoldMinutes()
                    : 15;

            LocalDateTime expireAt = session.getReservationTime().plusMinutes(holdMinutes);
            if (LocalDateTime.now().isAfter(expireAt)) {
                session.setStatus(TableSessionStatus.CANCELLED);
                session.setClosedAt(LocalDateTime.now());
                tableSessionRepository.save(session);

                session.getTable().setStatus(DiningTableStatus.AVAILABLE);
                diningTableRepository.save(session.getTable());

                log.info("Auto-cancelled RESERVED session id={} (hold {} min after {})",
                        session.getId(), holdMinutes, session.getReservationTime());

                // BR-12: ghi nhật ký tự động hủy đặt trước
                try {
                    auditLogService.record(AuditEvent.builder()
                            .entityType("TableSession")
                            .entityId(session.getId())
                            .actionType(AuditAction.CANCEL)
                            .branchId(branch.getId())
                            .valueBefore("status=" + TableSessionStatus.RESERVED)
                            .valueAfter("status=" + TableSessionStatus.CANCELLED)
                            .reason("Tự động hủy đặt trước do quá " + holdMinutes
                                    + " phút sau giờ hẹn " + session.getReservationTime() + " (BR-29)")
                            .build());
                } catch (Exception e) {
                    log.error("Failed to write audit log for auto-cancel reservation session id={}", session.getId(), e);
                }
            }
        }
    }
}
