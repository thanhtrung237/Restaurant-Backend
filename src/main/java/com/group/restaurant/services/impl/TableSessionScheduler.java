package com.group.restaurant.services.impl;

import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.TableSession;
import com.group.restaurant.repositories.DiningTableRepository;
import com.group.restaurant.repositories.TableSessionRepository;
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
            }
        }
    }
}
