package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.tableSession.TableSessionOpenRequest;
import com.group.restaurant.dtos.request.tableSession.TableSessionUpdateRequest;
import com.group.restaurant.dtos.response.tableSession.TableSessionResponse;
import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.TableSessionMapper;
import com.group.restaurant.models.DiningTable;
import com.group.restaurant.models.TableSession;
import com.group.restaurant.repositories.DiningTableRepository;
import com.group.restaurant.repositories.OrderRepository;
import com.group.restaurant.repositories.TableSessionRepository;
import com.group.restaurant.services.TableSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TableSessionServiceImpl implements TableSessionService {

    private final TableSessionRepository tableSessionRepository;
    private final DiningTableRepository diningTableRepository;
    private final OrderRepository orderRepository;
    private final TableSessionMapper tableSessionMapper;

    @Override
    public TableSessionResponse openSession(TableSessionOpenRequest request) {
        DiningTable table = diningTableRepository.findById(request.getTableId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bàn với ID: " + request.getTableId()));

        if (tableSessionRepository.findActiveSessionByTableId(request.getTableId()).isPresent()) {
            throw new ConflictException("Bàn " + table.getName() + " đang có phiên hoạt động chưa đóng");
        }

        TableSession session = tableSessionMapper.toEntity(request);
        session.setTable(table);
        session.setOpenedAt(LocalDateTime.now());
        session.setStatus(TableSessionStatus.SERVING);

        table.setStatus(DiningTableStatus.OCCUPIED);
        diningTableRepository.save(table);

        TableSession savedSession = tableSessionRepository.save(session);
        return buildTableSessionResponse(savedSession);
    }

    @Override
    public TableSessionResponse updateSession(Long id, TableSessionUpdateRequest request) {
        TableSession session = findSessionById(id);
        if (session.getStatus() == TableSessionStatus.CLOSED || session.getStatus() == TableSessionStatus.CANCELLED) {
            throw new ConflictException("Chỉ có thể cập nhật phiên làm việc đang mở");
        }

        tableSessionMapper.updateEntity(session, request);
        TableSession updatedSession = tableSessionRepository.save(session);
        return buildTableSessionResponse(updatedSession);
    }

    @Override
    @Transactional(readOnly = true)
    public TableSessionResponse getSessionById(Long id) {
        TableSession session = findSessionById(id);
        return buildTableSessionResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public TableSessionResponse getOpenSessionByTable(Long tableId) {
        if (!diningTableRepository.existsById(tableId)) {
            throw new ResourceNotFoundException("Không tìm thấy bàn với ID: " + tableId);
        }

        TableSession session = tableSessionRepository.findActiveSessionByTableId(tableId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phiên làm việc đang mở cho bàn ID: " + tableId));

        return buildTableSessionResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableSessionResponse> getOpenSessionsByBranch(Long branchId) {
        List<TableSession> sessions = tableSessionRepository.findActiveSessionsByBranchId(branchId);
        return sessions.stream()
                .map(this::buildTableSessionResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableSessionResponse> getSessionsByTable(Long tableId) {
        if (!diningTableRepository.existsById(tableId)) {
            throw new ResourceNotFoundException("Không tìm thấy bàn với ID: " + tableId);
        }

        List<TableSession> sessions = tableSessionRepository.findByTableId(tableId);
        return sessions.stream()
                .map(this::buildTableSessionResponse)
                .collect(Collectors.toList());
    }

    @Override
    public TableSessionResponse closeSession(Long id) {
        TableSession session = findSessionById(id);
        if (session.getStatus() == TableSessionStatus.CLOSED) {
            throw new ConflictException("Phiên này đã đóng trước đó");
        }

        session.setStatus(TableSessionStatus.CLOSED);
        session.setClosedAt(LocalDateTime.now());

        DiningTable table = session.getTable();
        table.setStatus(DiningTableStatus.AVAILABLE);
        diningTableRepository.save(table);

        TableSession updatedSession = tableSessionRepository.save(session);
        return buildTableSessionResponse(updatedSession);
    }

    @Override
    public TableSessionResponse cancelSession(Long id) {
        TableSession session = findSessionById(id);
        if (session.getStatus() == TableSessionStatus.CLOSED) {
            throw new ConflictException("Không thể hủy phiên bàn đã đóng trước đó");
        }

        session.setStatus(TableSessionStatus.CANCELLED);
        session.setClosedAt(LocalDateTime.now());

        DiningTable table = session.getTable();
        if (table != null) {
            table.setStatus(DiningTableStatus.AVAILABLE);
            diningTableRepository.save(table);
        }

        TableSession updatedSession = tableSessionRepository.save(session);
        return buildTableSessionResponse(updatedSession);
    }

    @Override
    public void deleteSession(Long id) {
        TableSession session = findSessionById(id);
        session.softDelete(null);
        tableSessionRepository.save(session);
    }

    private TableSession findSessionById(Long id) {
        return tableSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên bàn với ID: " + id));
    }

    private TableSessionResponse buildTableSessionResponse(TableSession session) {
        TableSessionResponse response = tableSessionMapper.toResponse(session);
        int orderCount = (int) orderRepository.countByTableSessionId(session.getId());
        response.setOrderCount(orderCount);
        return response;
    }
}
