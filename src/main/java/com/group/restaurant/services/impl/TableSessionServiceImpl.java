package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.tableSession.*;
import com.group.restaurant.dtos.response.tableSession.TableSessionResponse;
import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.TableSessionMapper;
import com.group.restaurant.models.Branch;
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

    // Tạo phiên mới
    // reservationTime == null → quét QR → WAITING_FOR_OPEN
    // reservationTime != null → đặt bàn trước → RESERVED
    @Override
    public TableSessionResponse createSession(TableSessionCreateRequest request) {
        DiningTable table = findTable(request.getTableId());

        // chặn nếu bàn đã có phiên đang hoạt động
        tableSessionRepository.findActiveSessionByTableId(table.getId()).ifPresent(existing -> {
            throw new ConflictException("Bàn " + table.getName() + " đang có phiên hoạt động chưa kết thúc");
        });

        boolean isReservation = request.getReservationTime() != null;

        TableSession session = TableSession.builder()
                .table(table)
                .branch(table.getBranch())
                .status(isReservation ? TableSessionStatus.RESERVED : TableSessionStatus.WAITING_FOR_OPEN)
                .build();

        if (isReservation) {
            // Đặt bàn trước
            if (request.getReservationName() == null || request.getReservationName().isBlank()) {
                throw new InvalidParamException("Đặt bàn trước cần nhập tên khách");
            }
            if (request.getReservationPhone() == null || request.getReservationPhone().isBlank()) {
                throw new InvalidParamException("Đặt bàn trước cần nhập số điện thoại khách");
            }
            session.setReservationName(request.getReservationName());
            session.setReservationPhone(request.getReservationPhone());
            session.setReservationTime(request.getReservationTime());
            session.setGuestCount(request.getReservedGuestCount());
            session.setReservationNote(request.getReservationNote());

            table.setStatus(DiningTableStatus.RESERVED);
        } else {
            // Quét QR — lưu lại số khách khách tự chọn để Phục vụ xác nhận mở bàn
            // không phải nhập lại (hiển thị sẵn trong TableSessionConfirmOpenRequest ở FE).
            session.setGuestCount(request.getGuestCount());
            table.setStatus(DiningTableStatus.OCCUPIED);
        }

        diningTableRepository.save(table);
        TableSession saved = tableSessionRepository.save(session);
        return buildResponse(saved);
    }


    // Phục vụ xác nhận mở bàn → SERVING
    // Áp dụng cho cả WAITING_FOR_OPEN và RESERVED
    @Override
    public TableSessionResponse confirmOpen(Long id, TableSessionConfirmOpenRequest request) {
        TableSession session = findSession(id);

        if (session.getStatus() != TableSessionStatus.WAITING_FOR_OPEN
                && session.getStatus() != TableSessionStatus.RESERVED) {
            throw new ConflictException("Chỉ có thể xác nhận mở bàn với phiên ở trạng thái CHỜ MỞ BÀN hoặc ĐẶT TRƯỚC");
        }

        // snapshot VAT và phí dịch vụ từ cấu hình chi nhánh tại thời điểm mở phiên
        Branch branch = session.getTable().getBranch();
        session.setVatRateAtOpen(branch.getVatRate());
        session.setServiceFeeRateAtOpen(branch.getServiceFeeRate());

        session.setGuestCount(request.getGuestCount());
        if (request.getNote() != null) {
            session.setNote(request.getNote());
        }
        session.setStatus(TableSessionStatus.SERVING);
        session.setOpenedAt(LocalDateTime.now());

        DiningTable table = session.getTable();
        table.setStatus(DiningTableStatus.OCCUPIED);
        diningTableRepository.save(table);

        return buildResponse(tableSessionRepository.save(session));
    }

    // Hủy đặt bàn trước: RESERVED → CANCELLED
    @Override
    public TableSessionResponse cancelReservation(Long id, TableSessionCancelReservationRequest request) {
        TableSession session = findSession(id);

        if (session.getStatus() != TableSessionStatus.RESERVED) {
            throw new ConflictException("Chỉ có thể hủy phiên đang ở trạng thái ĐẶT TRƯỚC");
        }

        session.setStatus(TableSessionStatus.CANCELLED);
        session.setClosedAt(LocalDateTime.now());
        if (request != null && request.getReason() != null) {
            session.setNote(request.getReason());
        }

        DiningTable table = session.getTable();
        table.setStatus(DiningTableStatus.AVAILABLE);
        diningTableRepository.save(table);

        return buildResponse(tableSessionRepository.save(session));
    }

    // Yêu cầu thanh toán: SERVING → WAITING_FOR_PAY
    @Override
    public TableSessionResponse requestPayment(Long id) {
        TableSession session = findSession(id);

        if (session.getStatus() != TableSessionStatus.SERVING) {
            throw new ConflictException("Chỉ có thể yêu cầu thanh toán với phiên đang ở trạng thái ĐANG PHỤC VỤ");
        }

        session.setStatus(TableSessionStatus.WAITING_FOR_PAY);
        return buildResponse(tableSessionRepository.save(session));
    }

    // QL chi nhánh mở lại bàn: WAITING_FOR_PAY → SERVING
    @Override
    public TableSessionResponse reopenSession(Long id) {
        TableSession session = findSession(id);

        if (session.getStatus() != TableSessionStatus.WAITING_FOR_PAY) {
            throw new ConflictException("Chỉ có thể mở lại bàn với phiên đang ở trạng thái CHỜ THANH TOÁN");
        }

        session.setStatus(TableSessionStatus.SERVING);
        return buildResponse(tableSessionRepository.save(session));
    }


    @Override
    public TableSessionResponse openSession(TableSessionOpenRequest request) {
        DiningTable table = findTable(request.getTableId());

        tableSessionRepository.findActiveSessionByTableId(table.getId()).ifPresent(existing -> {
            throw new ConflictException("Bàn " + table.getName() + " đang có phiên hoạt động chưa kết thúc");
        });

        Branch branch = table.getBranch();

        TableSession session = TableSession.builder()
                .table(table)
                .branch(branch)
                .guestCount(request.getGuestCount())
                .note(request.getNote())
                .coverFeePerGuestAtOpen(request.getCoverFeePerGuestAtOpen())
                .customerIdRef(request.getCustomerIdRef())
                .openedAt(LocalDateTime.now())
                .status(TableSessionStatus.SERVING)
                // snapshot tại thời điểm mở phiên
                .vatRateAtOpen(branch.getVatRate())
                .serviceFeeRateAtOpen(branch.getServiceFeeRate())
                .build();

        table.setStatus(DiningTableStatus.OCCUPIED);
        diningTableRepository.save(table);

        return buildResponse(tableSessionRepository.save(session));
    }

    @Override
    public TableSessionResponse updateSession(Long id, TableSessionUpdateRequest request) {
        TableSession session = findSession(id);
        if (session.getStatus() == TableSessionStatus.CLOSED
                || session.getStatus() == TableSessionStatus.CANCELLED) {
            throw new ConflictException("Chỉ có thể cập nhật phiên làm việc đang mở");
        }
        tableSessionMapper.updateEntity(session, request);
        return buildResponse(tableSessionRepository.save(session));
    }

    @Override
    @Transactional(readOnly = true)
    public TableSessionResponse getSessionById(Long id) {
        return buildResponse(findSession(id));
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
        return buildResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableSessionResponse> getOpenSessionsByBranch(Long branchId) {
        return tableSessionRepository.findActiveSessionsByBranchId(branchId)
                .stream().map(this::buildResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableSessionResponse> getSessionsByTable(Long tableId) {
        if (!diningTableRepository.existsById(tableId)) {
            throw new ResourceNotFoundException("Không tìm thấy bàn với ID: " + tableId);
        }
        return tableSessionRepository.findByTableId(tableId)
                .stream().map(this::buildResponse).collect(Collectors.toList());
    }

    @Override
    public TableSessionResponse closeSession(Long id) {
        TableSession session = findSession(id);
        if (session.getStatus() == TableSessionStatus.CLOSED) {
            throw new ConflictException("Phiên này đã đóng trước đó");
        }
        session.setStatus(TableSessionStatus.CLOSED);
        session.setClosedAt(LocalDateTime.now());

        DiningTable table = session.getTable();
        table.setStatus(DiningTableStatus.AVAILABLE);
        diningTableRepository.save(table);

        return buildResponse(tableSessionRepository.save(session));
    }

    @Override
    public TableSessionResponse cancelSession(Long id) {
        TableSession session = findSession(id);
        if (session.getStatus() == TableSessionStatus.CLOSED) {
            throw new ConflictException("Không thể hủy phiên bàn đã đóng");
        }
        session.setStatus(TableSessionStatus.CANCELLED);
        session.setClosedAt(LocalDateTime.now());

        DiningTable table = session.getTable();
        if (table != null) {
            table.setStatus(DiningTableStatus.AVAILABLE);
            diningTableRepository.save(table);
        }
        return buildResponse(tableSessionRepository.save(session));
    }

    @Override
    public void deleteSession(Long id) {
        TableSession session = findSession(id);
        session.softDelete(null);
        tableSessionRepository.save(session);
    }

    private TableSession findSession(Long id) {
        return tableSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên bàn với ID: " + id));
    }

    private DiningTable findTable(Long tableId) {
        return diningTableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bàn với ID: " + tableId));
    }

    private TableSessionResponse buildResponse(TableSession session) {
        TableSessionResponse response = tableSessionMapper.toResponse(session);
        int orderCount = (int) orderRepository.countByTableSessionId(session.getId());
        response.setOrderCount(orderCount);
        return response;
    }
}