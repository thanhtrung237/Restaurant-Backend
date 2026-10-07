package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.tableSession.*;
import com.group.restaurant.dtos.response.tableSession.TableSessionResponse;
import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.TableSessionMapper;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.DiningTable;
import com.group.restaurant.models.OrderDetail;
import com.group.restaurant.models.TableSession;
import com.group.restaurant.repositories.DiningTableRepository;
import com.group.restaurant.repositories.InvoiceRepository;
import com.group.restaurant.repositories.OrderDetailRepository;
import com.group.restaurant.repositories.OrderRepository;
import com.group.restaurant.repositories.TableSessionRepository;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.OrderNotificationService;
import com.group.restaurant.services.TableSessionService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.utils.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final OrderDetailRepository orderDetailRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditLogService auditLogService;
    private final TenantGuard tenantGuard;
    private final OrderNotificationService orderNotificationService;

    // Tạo phiên mới từ quét QR → WAITING_FOR_OPEN (public, FR-CUS-01)
    @Override
    public TableSessionResponse createSession(TableSessionCreateRequest request) {
        DiningTable table = findTable(request.getTableId());

        // chặn nếu bàn đã có phiên đang hoạt động
        tableSessionRepository.findActiveSessionByTableId(table.getId()).ifPresent(existing -> {
            throw new ConflictException("Bàn " + table.getName() + " đang có phiên hoạt động chưa kết thúc");
        });

        TableSession session = TableSession.builder()
                .table(table)
                .branch(table.getBranch())
                .status(TableSessionStatus.WAITING_FOR_OPEN)
                .guestCount(request.getGuestCount())
                .build();

        table.setStatus(DiningTableStatus.OCCUPIED);
        diningTableRepository.save(table);
        TableSession saved = tableSessionRepository.save(session);
        return buildResponse(saved);
    }

    // Đặt bàn trước → RESERVED (FR-TBL-03, BR-29 — chỉ Phục vụ / QL chi nhánh)
    @Override
    public TableSessionResponse reserveSession(TableSessionReservationRequest request) {
        DiningTable table = findTable(request.getTableId());
        tenantGuard.assertBranch(table.getBranch());

        // chặn nếu bàn đã có phiên đang hoạt động (BR-01, BR-29)
        tableSessionRepository.findActiveSessionByTableId(table.getId()).ifPresent(existing -> {
            throw new ConflictException("Bàn " + table.getName() + " đang có phiên hoạt động chưa kết thúc");
        });

        TableSession session = TableSession.builder()
                .table(table)
                .branch(table.getBranch())
                .status(TableSessionStatus.RESERVED)
                .reservationName(request.getReservationName())
                .reservationPhone(request.getReservationPhone())
                .reservationTime(request.getReservationTime())
                .guestCount(request.getGuestCount())
                .reservationNote(request.getReservationNote())
                .build();

        table.setStatus(DiningTableStatus.RESERVED);
        diningTableRepository.save(table);
        TableSession saved = tableSessionRepository.save(session);

        auditLogService.record(AuditEvent.builder()
                .entityType("TableSession")
                .entityId(saved.getId())
                .actionType(AuditAction.STATUS_CHANGE)
                .branchId(table.getBranch().getId())
                .valueAfter("status=" + TableSessionStatus.RESERVED
                        + "; reservationTime=" + request.getReservationTime()
                        + "; guest=" + request.getReservationName())
                .reason("Đặt bàn trước cho khách " + request.getReservationName())
                .build());

        return buildResponse(saved);
    }


    // Phục vụ xác nhận mở bàn → SERVING
    // Áp dụng cho cả WAITING_FOR_OPEN và RESERVED
    @Override
    public TableSessionResponse confirmOpen(Long id, TableSessionConfirmOpenRequest request) {
        TableSession session = findSession(id);
        tenantGuard.assertBranch(session.getBranch());

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
        tenantGuard.assertBranch(session.getBranch());

        if (session.getStatus() != TableSessionStatus.RESERVED) {
            throw new ConflictException("Chỉ có thể hủy phiên đang ở trạng thái ĐẶT TRƯỚC");
        }

        session.setStatus(TableSessionStatus.CANCELLED);
        session.setClosedAt(LocalDateTime.now());
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Hủy đặt trước";
        session.setNote(reason);

        DiningTable table = session.getTable();
        table.setStatus(DiningTableStatus.AVAILABLE);
        diningTableRepository.save(table);

        TableSessionResponse response = buildResponse(tableSessionRepository.save(session));

        // BR-12: ghi nhật ký đổi trạng thái phiên
        auditLogService.record(AuditEvent.builder()
                .entityType("TableSession")
                .entityId(session.getId())
                .actionType(AuditAction.CANCEL)
                .branchId(session.getBranch() != null ? session.getBranch().getId() : null)
                .valueBefore("status=" + TableSessionStatus.RESERVED)
                .valueAfter("status=" + TableSessionStatus.CANCELLED)
                .reason(reason)
                .build());

        return response;
    }

    // Yêu cầu thanh toán: SERVING → WAITING_FOR_PAY
    @Override
    public TableSessionResponse requestPayment(Long id) {
        TableSession session = findSession(id);

        if (session.getStatus() != TableSessionStatus.SERVING) {
            throw new ConflictException("Chỉ có thể yêu cầu thanh toán với phiên đang ở trạng thái ĐANG PHỤC VỤ");
        }

        if (!hasBillableDetail(session.getId())) {
            throw new ConflictException("Chưa có món để tính tiền");
        }

        session.setStatus(TableSessionStatus.WAITING_FOR_PAY);
        TableSession saved = tableSessionRepository.save(session);

        String tableNo = session.getTable() != null ? session.getTable().getName() : "";
        Long branchId = session.getBranch() != null ? session.getBranch().getId() : null;
        if (branchId != null) {
            orderNotificationService.notifyPaymentRequested(branchId, session.getId(), tableNo);
        }

        return buildResponse(saved);
    }

    // QL chi nhánh mở lại bàn: WAITING_FOR_PAY → SERVING
    @Override
    public TableSessionResponse reopenSession(Long id) {
        TableSession session = findSession(id);
        tenantGuard.assertBranch(session.getBranch());

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
        tenantGuard.assertBranch(branchId);
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
        tenantGuard.assertBranch(session.getBranch());
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
    public TableSessionResponse cancelSession(Long id, String reason) {
        TableSession session = findSession(id);
        tenantGuard.assertBranch(session.getBranch());
        if (session.getStatus() == TableSessionStatus.CLOSED) {
            throw new ConflictException("Không thể hủy phiên bàn đã đóng");
        }
        // QĐ-02: phiên CHỜ THANH TOÁN chỉ hủy thẳng được khi CHƯA phát sinh tiền —
        // tổng tính được = 0 và chưa có hoá đơn đã phát hành / đã thu.
        if (session.getStatus() == TableSessionStatus.WAITING_FOR_PAY) {
            assertCancellableWhileWaitingForPay(session);
        }
        // Bang 2.2: "Huy phien - DANG PHUC VU -> DA HUY, QL chi nhanh, bat buoc ly do,
        // ghi nhat ky". Truoc day API khong nhan ly do va khong ghi nhat ky dong nao.
        if (reason == null || reason.isBlank()) {
            throw new InvalidParamException("Phải nhập lý do huỷ phiên");
        }

        TableSessionStatus previous = session.getStatus();
        session.setStatus(TableSessionStatus.CANCELLED);
        session.setClosedAt(LocalDateTime.now());
        // Luu vao cot `note` co san - khong them cot moi. cancelReservation cung lam vay.
        session.setNote(reason.trim());

        DiningTable table = session.getTable();
        if (table != null) {
            table.setStatus(DiningTableStatus.AVAILABLE);
            diningTableRepository.save(table);
        }
        TableSessionResponse response = buildResponse(tableSessionRepository.save(session));

        auditLogService.record(AuditEvent.builder()
                .entityType("TableSession")
                .entityId(session.getId())
                .actionType(AuditAction.CANCEL)
                .branchId(session.getBranch() != null ? session.getBranch().getId() : null)
                .valueBefore("status=" + previous)
                .valueAfter("status=" + TableSessionStatus.CANCELLED)
                .reason(reason.trim())
                .build());

        return response;
    }

    /**
     * QĐ-02 — hủy thẳng phiên CHỜ THANH TOÁN.
     *
     * Trước đây giao diện phải "mở lại bàn rồi mới hủy": hai lời gọi, và kẹt nửa chừng nếu
     * bước hai lỗi. Phiên 0đ chưa phát sinh tiền nên không có gì để đối soát.
     *
     * Hai chốt chặn, cả hai đều là dữ liệu tiền bạc:
     * 1. Tổng tiền tính được phải bằng 0 (cùng tiêu chí với hoá đơn — xem shouldCharge).
     * 2. Không có hoá đơn ISSUED (đã phát hành, BR-09 bất biến) hay PAID (đã thu tiền).
     *    Hoá đơn PENDING 0đ thì vẫn cho hủy: nó chỉ là bản nháp, thường do lỗi mục 17 sinh ra.
     */
    private void assertCancellableWhileWaitingForPay(TableSession session) {
        BigDecimal total = billableTotal(session.getId());
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            throw new ConflictException(
                    "Phiên đang chờ thanh toán và đã có món tính tiền — hãy thu tiền rồi đóng bàn, "
                    + "hoặc mở lại bàn trước khi hủy");
        }
        invoiceRepository.findByTableSessionId(session.getId())
                .filter(invoice -> invoice.getStatus() == InvoiceStatus.ISSUED
                        || invoice.getStatus() == InvoiceStatus.PAID)
                .ifPresent(invoice -> {
                    throw new ConflictException("Phiên đã có hoá đơn " + invoice.getInvoiceNumber()
                            + " (" + invoice.getStatus() + ") — không thể hủy phiên");
                });
    }

    /** Tổng tiền các dòng món TÍNH TIỀN — cùng công thức với subTotal của hoá đơn. */
    private BigDecimal billableTotal(Long sessionId) {
        return orderDetailRepository.findByTableSessionId(sessionId).stream()
                .filter(d -> d.getStatus() != OrderDetailStatus.CANCELLED
                        || Boolean.TRUE.equals(d.getIsBillable()))
                .map(d -> {
                    if (d.getPrice() != null) return d.getPrice();
                    if (d.getPriceAtOrderTime() == null) return BigDecimal.ZERO;
                    BigDecimal qty = d.getWeightKg() != null
                            ? d.getWeightKg()
                            : BigDecimal.valueOf(d.getQuantity() != null ? d.getQuantity() : 1);
                    return d.getPriceAtOrderTime().multiply(qty);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Con dong mon nao TINH TIEN khong - cung tieu chi voi
     * InvoiceServiceImpl.shouldChargeDetail: dong chua huy thi tinh; dong da huy chi
     * tinh khi co isBillable bat (khach doi y - BR-24).
     */
    private boolean hasBillableDetail(Long sessionId) {
        return orderDetailRepository.findByTableSessionId(sessionId).stream()
                .anyMatch(d -> d.getStatus() != OrderDetailStatus.CANCELLED
                        || Boolean.TRUE.equals(d.getIsBillable()));
    }

    @Override
    public void deleteSession(Long id) {
        TableSession session = findSession(id);
        tenantGuard.assertBranch(session.getBranch());
        session.softDelete(null);
        tableSessionRepository.save(session);
    }

    @Override
    public void callStaff(Long id, Long orderDetailId) {
        TableSession session = findSession(id);
        String tableNo = session.getTable() != null ? session.getTable().getName() : "";
        Long branchId = session.getBranch() != null ? session.getBranch().getId() : null;

        // FR-CUS-07: nếu có orderDetailId, lấy tên món để gửi kèm thông báo
        String dishName = "Khách gọi nhân viên hỗ trợ tại bàn";
        if (orderDetailId != null) {
            OrderDetail detail = orderDetailRepository.findById(orderDetailId).orElse(null);
            if (detail != null && detail.getDish() != null) {
                dishName = detail.getDish().getName();
            }
        }

        if (branchId != null) {
            orderNotificationService.notifyStaffCall(branchId, session.getId(), orderDetailId, tableNo, dishName);
        }
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