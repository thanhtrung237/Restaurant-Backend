package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.tableSession.*;
import com.group.restaurant.dtos.response.tableSession.TableSessionResponse;
import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.enums.OperationalPermission;
import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.TableSessionMapper;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.DiningTable;
import com.group.restaurant.models.Order;
import com.group.restaurant.models.OrderDetail;
import com.group.restaurant.models.TableSession;
import com.group.restaurant.repositories.DiningTableRepository;
import com.group.restaurant.repositories.InvoiceRepository;
import com.group.restaurant.repositories.OrderDetailRepository;
import com.group.restaurant.repositories.OrderRepository;
import com.group.restaurant.repositories.PaymentRepository;
import com.group.restaurant.repositories.TableSessionRepository;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.OperationalPermissionService;
import com.group.restaurant.services.OrderNotificationService;
import com.group.restaurant.services.TableSessionService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.utils.TenantGuard;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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
    private final PaymentRepository paymentRepository;
    private final AuditLogService auditLogService;
    private final TenantGuard tenantGuard;
    private final OperationalPermissionService permissions;
    private final OrderNotificationService orderNotificationService;

    // Staff API or validated guest admission creates WAITING_FOR_OPEN (FR-CUS-01).
    @Override
    public TableSessionResponse createSession(TableSessionCreateRequest request) {
        DiningTable table = findTable(request.getTableId());

        tenantGuard.assertBranchAssigned(table.getBranch().getId());
        // chặn nếu bàn đã có phiên đang hoạt động
        tableSessionRepository
                .findActiveForGuestAdmission(table.getId())
                .ifPresent(
                        existing -> {
                            throw new ConflictException(
                                    "Bàn "
                                            + table.getName()
                                            + " đang có phiên hoạt động chưa kết thúc");
                        });

        TableSession session =
                TableSession.builder()
                        .table(table)
                        .branch(table.getBranch())
                        .status(TableSessionStatus.WAITING_FOR_OPEN)
                        .guestCount(request.getGuestCount())
                        .build();

        table.setStatus(DiningTableStatus.OCCUPIED);
        diningTableRepository.save(table);
        TableSession saved = tableSessionRepository.save(session);
        orderNotificationService.notifySessionUpdated(saved);
        return buildResponse(saved);
    }

    // Đặt bàn trước theo quyền tại chi nhánh (FR-TBL-03, BR-29).
    @Override
    public TableSessionResponse reserveSession(TableSessionReservationRequest request) {
        DiningTable table =
                diningTableRepository
                        .findForGuestAdmission(request.getTableId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bàn."));
        permissions.require(table.getBranch().getId(), OperationalPermission.RESERVE_TABLE);
        if (table.getStatus() != DiningTableStatus.AVAILABLE) {
            throw new ConflictException("Chỉ có thể đặt trước bàn đang trống.");
        }

        // chặn nếu bàn đã có phiên đang hoạt động (BR-01, BR-29)
        tableSessionRepository
                .findActiveForGuestAdmission(table.getId())
                .ifPresent(
                        existing -> {
                            throw new ConflictException(
                                    "Bàn "
                                            + table.getName()
                                            + " đang có phiên hoạt động chưa kết thúc");
                        });

        TableSession session =
                TableSession.builder()
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
        orderNotificationService.notifySessionUpdated(saved);

        auditLogService.record(
                AuditEvent.builder()
                        .entityType("TableSession")
                        .entityId(saved.getId())
                        .actionType(AuditAction.STATUS_CHANGE)
                        .branchId(table.getBranch().getId())
                        .valueAfter(
                                "status="
                                        + TableSessionStatus.RESERVED
                                        + "; reservationTime="
                                        + request.getReservationTime()
                                        + "; guest="
                                        + request.getReservationName())
                        .reason("Đặt bàn trước cho khách " + request.getReservationName())
                        .build());

        return buildResponse(saved);
    }

    // Phục vụ xác nhận mở bàn → SERVING
    // Áp dụng cho cả WAITING_FOR_OPEN và RESERVED
    @Override
    public TableSessionResponse confirmOpen(Long id, TableSessionConfirmOpenRequest request) {
        TableSession session = findSession(id);
        tenantGuard.assertBranchAssigned(session.getBranch().getId());

        if (session.getStatus() != TableSessionStatus.WAITING_FOR_OPEN
                && session.getStatus() != TableSessionStatus.RESERVED) {
            throw new ConflictException(
                    "Chỉ có thể xác nhận mở bàn với phiên ở trạng thái CHỜ MỞ BÀN hoặc ĐẶT TRƯỚC");
        }

        // BR-29: khi xác nhận khách đến từ phiên ĐẶT TRƯỚC, bắt buộc nhập số khách thực tế
        if (session.getStatus() == TableSessionStatus.RESERVED
                && (request.getGuestCount() == null || request.getGuestCount() <= 0)) {
            throw new InvalidParamException(
                    "Bắt buộc nhập số khách thực tế khi xác nhận khách đặt trước đến (BR-29)");
        }

        // snapshot VAT và phí dịch vụ từ cấu hình chi nhánh tại thời điểm mở phiên
        Branch branch = session.getTable().getBranch();
        session.setVatRateAtOpen(branch.getVatRate());
        session.setServiceFeeRateAtOpen(branch.getServiceFeeRate());

        TableSessionStatus previousStatus = session.getStatus();
        session.setGuestCount(request.getGuestCount());
        if (request.getNote() != null) {
            session.setNote(request.getNote());
        }
        session.setStatus(TableSessionStatus.SERVING);
        session.setOpenedAt(LocalDateTime.now());

        DiningTable table = session.getTable();
        table.setStatus(DiningTableStatus.OCCUPIED);
        diningTableRepository.save(table);

        TableSession saved = tableSessionRepository.save(session);
        orderNotificationService.notifySessionUpdated(saved);

        // BR-12: ghi nhật ký đổi trạng thái phiên khi mở bàn
        auditLogService.record(
                AuditEvent.builder()
                        .entityType("TableSession")
                        .entityId(saved.getId())
                        .actionType(AuditAction.STATUS_CHANGE)
                        .branchId(branch.getId())
                        .valueBefore("status=" + previousStatus)
                        .valueAfter(
                                "status="
                                        + TableSessionStatus.SERVING
                                        + "; guestCount="
                                        + request.getGuestCount()
                                        + "; openedAt="
                                        + saved.getOpenedAt())
                        .reason(
                                previousStatus == TableSessionStatus.RESERVED
                                        ? "Khách đặt trước đã đến, xác nhận mở bàn"
                                        : "Xác nhận mở bàn")
                        .build());

        return buildResponse(saved);
    }

    // Hủy đặt bàn trước: RESERVED → CANCELLED
    @Override
    public TableSessionResponse cancelReservation(
            Long id, TableSessionCancelReservationRequest request) {
        TableSession session = findSession(id);
        permissions.require(session.getBranch().getId(), OperationalPermission.RESERVE_TABLE);

        if (session.getStatus() != TableSessionStatus.RESERVED) {
            throw new ConflictException("Chỉ có thể hủy phiên đang ở trạng thái ĐẶT TRƯỚC");
        }

        session.setStatus(TableSessionStatus.CANCELLED);
        session.setClosedAt(LocalDateTime.now());
        String reason =
                (request != null && request.getReason() != null)
                        ? request.getReason()
                        : "Hủy đặt trước";
        session.setNote(reason);

        DiningTable table = session.getTable();
        table.setStatus(DiningTableStatus.AVAILABLE);
        diningTableRepository.save(table);

        TableSessionResponse response = saveAndNotify(session);

        // BR-12: ghi nhật ký đổi trạng thái phiên
        auditLogService.record(
                AuditEvent.builder()
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
        TableSession session =
                tableSessionRepository
                        .findForUpdate(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy phiên bàn với ID: " + id));
        tenantGuard.assertBranchAssigned(session.getBranch().getId());
        if (session.isTakeaway()) {
            tenantGuard.assertBranchAssigned(
                    session.getBranch().getId(),
                    TenantGuard.ROLE_CASHIER,
                    TenantGuard.ROLE_BRANCH_MANAGER,
                    TenantGuard.ROLE_MANAGER);
        }

        if (session.getStatus() != TableSessionStatus.SERVING) {
            throw new ConflictException(
                    "Chỉ có thể yêu cầu thanh toán với phiên đang ở trạng thái ĐANG PHỤC VỤ");
        }

        if (!hasBillableDetail(session.getId())) {
            throw new ConflictException("Chưa có món để tính tiền");
        }

        session.setStatus(TableSessionStatus.WAITING_FOR_PAY);
        TableSession saved = tableSessionRepository.save(session);
        orderNotificationService.notifySessionUpdated(saved);

        String tableNo = session.getTable() != null ? session.getTable().getName() : "";
        Long branchId = session.getBranch() != null ? session.getBranch().getId() : null;
        if (branchId != null && !session.isTakeaway()) {
            orderNotificationService.notifyPaymentRequested(branchId, session.getId(), tableNo);
        }

        return buildResponse(saved);
    }

    // QL chi nhánh mở lại bàn: WAITING_FOR_PAY → SERVING
    @Override
    public TableSessionResponse reopenSession(Long id) {
        TableSession session =
                tableSessionRepository
                        .findForUpdate(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Không tìm thấy phiên bàn."));
        tenantGuard.assertBranch(session.getBranch());
        if (session.isTakeaway())
            throw new ConflictException("Không thể mở lại đơn mang về từ thao tác mở bàn.");

        if (session.getStatus() != TableSessionStatus.WAITING_FOR_PAY) {
            throw new ConflictException(
                    "Chỉ có thể mở lại bàn với phiên đang ở trạng thái CHỜ THANH TOÁN");
        }
        if (invoiceRepository
                .findBySessionForPayment(id)
                .filter(invoice -> invoice.getQuoteSharedAt() != null)
                .isPresent()) {
            throw new ConflictException(
                    "Tạm tính đã gửi khách. Hãy điều chỉnh món trên màn chốt bàn.");
        }

        session.setStatus(TableSessionStatus.SERVING);
        return saveAndNotify(session);
    }

    @Override
    public TableSessionResponse openSession(TableSessionOpenRequest request) {
        DiningTable table = findTable(request.getTableId());

        tenantGuard.assertBranchAssigned(table.getBranch().getId());
        tableSessionRepository
                .findActiveForGuestAdmission(table.getId())
                .ifPresent(
                        existing -> {
                            throw new ConflictException(
                                    "Bàn "
                                            + table.getName()
                                            + " đang có phiên hoạt động chưa kết thúc");
                        });

        Branch branch = table.getBranch();

        TableSession session =
                TableSession.builder()
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

        return saveAndNotify(session);
    }

    @Override
    public TableSessionResponse updateSession(Long id, TableSessionUpdateRequest request) {
        TableSession session = findSession(id);
        if (session.getStatus() == TableSessionStatus.CLOSED
                || session.getStatus() == TableSessionStatus.CANCELLED) {
            throw new ConflictException("Chỉ có thể cập nhật phiên làm việc đang mở");
        }
        tableSessionMapper.updateEntity(session, request);
        return saveAndNotify(session);
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
        TableSession session =
                tableSessionRepository
                        .findActiveSessionByTableId(tableId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy phiên làm việc đang mở cho bàn ID: "
                                                        + tableId));
        return buildResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableSessionResponse> getOpenSessionsByBranch(Long branchId) {
        tenantGuard.assertBranchAssigned(branchId);
        return tableSessionRepository.findActiveSessionsByBranchId(branchId).stream()
                .map(this::buildResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableSessionResponse> getSessionsByTable(Long tableId) {
        if (!diningTableRepository.existsById(tableId)) {
            throw new ResourceNotFoundException("Không tìm thấy bàn với ID: " + tableId);
        }
        return tableSessionRepository.findByTableId(tableId).stream()
                .map(this::buildResponse)
                .collect(Collectors.toList());
    }

    @Override
    public TableSessionResponse closeSession(Long id) {
        TableSession session = findSession(id);
        tenantGuard.assertBranch(session.getBranch());
        if (session.isTakeaway())
            throw new ConflictException("Đơn mang về được hoàn tất tại danh sách Mang về.");
        if (session.getStatus() == TableSessionStatus.RESERVED) {
            throw new ConflictException("Dùng thao tác hủy đặt bàn cho phiên đặt trước.");
        }
        if (session.getStatus() == TableSessionStatus.CLOSED) {
            throw new ConflictException("Phiên này đã đóng trước đó");
        }
        session.setStatus(TableSessionStatus.CLOSED);
        session.setClosedAt(LocalDateTime.now());

        DiningTable table = session.getTable();
        table.setStatus(DiningTableStatus.AVAILABLE);
        diningTableRepository.save(table);

        return saveAndNotify(session);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TableSessionResponse cancelSession(Long id, String reason) {
        TableSession session =
                tableSessionRepository
                        .findForUpdate(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Không tìm thấy phiên bàn với ID: " + id));
        tenantGuard.assertBranchAssigned(session.getBranch().getId(), TenantGuard.ROLE_BRANCH_MANAGER);
        if (session.getStatus() == TableSessionStatus.RESERVED) {
            permissions.require(session.getBranch().getId(), OperationalPermission.RESERVE_TABLE);
        }
        if (session.getStatus() == TableSessionStatus.CLOSED) {
            throw new ConflictException("Không thể hủy phiên bàn đã đóng");
        }
        if (session.getStatus() == TableSessionStatus.CANCELLED
                || session.getStatus() == TableSessionStatus.MERGED) {
            throw new ConflictException("Phiên bàn này đã kết thúc");
        }
        // Bang 2.2: "Huy phien - DANG PHUC VU -> DA HUY, QL chi nhanh, bat buoc ly do,
        // ghi nhat ky". Truoc day API khong nhan ly do va khong ghi nhat ky dong nao.
        if (reason == null || reason.isBlank()) {
            throw new InvalidParamException("Phải nhập lý do huỷ phiên");
        }
        if (session.getStatus() == TableSessionStatus.WAITING_FOR_PAY) {
            cancelUnpaidDraftForEmptySession(session);
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
        TableSessionResponse response = saveAndNotify(session);

        auditLogService.record(
                AuditEvent.builder()
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
     * Khi hủy phiên trống đang chờ thanh toán, khóa hóa đơn nháp để chặn thu tiền
     * đồng thời và hủy bản nháp cùng phiên.
     */
    private void cancelUnpaidDraftForEmptySession(TableSession session) {
        BigDecimal total = billableTotal(session.getId());
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            throw new ConflictException(
                    "Phiên đang chờ thanh toán và đã có món tính tiền — hãy thu tiền rồi đóng bàn, "
                            + "hoặc mở lại bàn trước khi hủy");
        }
        invoiceRepository
                .findBySessionForPayment(session.getId())
                .ifPresent(
                        invoice -> {
                            if ((invoice.getStatus() != InvoiceStatus.PENDING
                                            && invoice.getStatus() != InvoiceStatus.CANCELLED)
                                    || invoice.getEInvoiceRef() != null
                                    || paymentRepository.countByInvoiceId(invoice.getId()) > 0) {
                                throw new ConflictException(
                                        "Phiên đã phát sinh thanh toán hoặc phát hành hóa đơn, không thể hủy");
                            }
                            if (invoice.getStatus() == InvoiceStatus.PENDING) {
                                invoice.setStatus(InvoiceStatus.CANCELLED);
                            }
                        });
    }

    /** Tổng tiền các dòng món TÍNH TIỀN — cùng công thức với subTotal của hoá đơn. */
    private BigDecimal billableTotal(Long sessionId) {
        return orderDetailRepository.findByTableSessionId(sessionId).stream()
                .filter(
                        d ->
                                d.getStatus() != OrderDetailStatus.CANCELLED
                                        || Boolean.TRUE.equals(d.getIsBillable()))
                .map(
                        d -> {
                            if (d.getPrice() != null) return d.getPrice();
                            if (d.getPriceAtOrderTime() == null) return BigDecimal.ZERO;
                            BigDecimal qty =
                                    d.getWeightKg() != null
                                            ? d.getWeightKg()
                                            : BigDecimal.valueOf(
                                                    d.getQuantity() != null ? d.getQuantity() : 1);
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
                .anyMatch(
                        d ->
                                d.getStatus() != OrderDetailStatus.CANCELLED
                                        || Boolean.TRUE.equals(d.getIsBillable()));
    }

    @Override
    public void deleteSession(Long id) {
        TableSession session = findSession(id);
        tenantGuard.assertBranch(session.getBranch());
        session.softDelete(null);
        tableSessionRepository.save(session);
        orderNotificationService.notifySessionUpdated(session);
    }

    private TableSessionResponse saveAndNotify(TableSession session) {
        TableSession saved = tableSessionRepository.save(session);
        orderNotificationService.notifySessionUpdated(saved);
        return buildResponse(saved);
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
            orderNotificationService.notifyStaffCall(
                    branchId, session.getId(), orderDetailId, tableNo, dishName);
        }
    }

    @Override
    public TableSessionResponse transferTable(Long sessionId, TableSessionTransferRequest request) {
        TableSession session = findSession(sessionId);
        tenantGuard.assertBranch(session.getBranch());
        if (session.isTakeaway()) throw new ConflictException("Đơn mang về không thể chuyển bàn.");

        if (session.getStatus() == TableSessionStatus.CLOSED
                || session.getStatus() == TableSessionStatus.CANCELLED
                || session.getStatus() == TableSessionStatus.MERGED) {
            throw new ConflictException(
                    "Không thể chuyển bàn với phiên ở trạng thái " + session.getStatus());
        }

        DiningTable sourceTable = session.getTable();
        DiningTable targetTable = findTable(request.getTargetTableId());
        tenantGuard.assertBranch(targetTable.getBranch());

        if (!java.util.Objects.equals(
                sourceTable.getBranch().getId(), targetTable.getBranch().getId())) {
            throw new InvalidParamException("Không thể chuyển bàn sang chi nhánh khác");
        }

        if (sourceTable.getId().equals(targetTable.getId())) {
            throw new InvalidParamException("Bàn đích phải khác bàn hiện tại");
        }

        // Chặn nếu bàn đích đang có phiên hoạt động hoặc không ở trạng thái AVAILABLE
        tableSessionRepository
                .findActiveSessionByTableId(targetTable.getId())
                .ifPresent(
                        active -> {
                            throw new ConflictException(
                                    "Bàn "
                                            + targetTable.getName()
                                            + " đang có phiên hoạt động khác");
                        });
        if (targetTable.getStatus() != DiningTableStatus.AVAILABLE) {
            throw new ConflictException(
                    "Bàn " + targetTable.getName() + " không ở trạng thái trống");
        }

        // Cập nhật bàn
        sourceTable.setStatus(DiningTableStatus.AVAILABLE);
        diningTableRepository.save(sourceTable);

        targetTable.setStatus(DiningTableStatus.OCCUPIED);
        diningTableRepository.save(targetTable);

        session.setTable(targetTable);
        TableSession saved = tableSessionRepository.save(session);
        orderNotificationService.notifySessionUpdated(saved);

        auditLogService.record(
                AuditEvent.builder()
                        .entityType("TableSession")
                        .entityId(saved.getId())
                        .actionType(AuditAction.STATUS_CHANGE)
                        .branchId(saved.getBranch() != null ? saved.getBranch().getId() : null)
                        .valueBefore(
                                "table="
                                        + sourceTable.getName()
                                        + " (id="
                                        + sourceTable.getId()
                                        + ")")
                        .valueAfter(
                                "table="
                                        + targetTable.getName()
                                        + " (id="
                                        + targetTable.getId()
                                        + ")")
                        .reason(
                                "Chuyển bàn từ "
                                        + sourceTable.getName()
                                        + " sang "
                                        + targetTable.getName())
                        .build());

        return buildResponse(saved);
    }

    @Override
    public TableSessionResponse mergeTable(TableSessionMergeRequest request) {
        if (request.getSourceSessionId().equals(request.getTargetSessionId())) {
            throw new InvalidParamException("Không thể gộp một phiên vào chính nó");
        }

        TableSession sourceSession = findSession(request.getSourceSessionId());
        TableSession targetSession = findSession(request.getTargetSessionId());

        tenantGuard.assertBranch(sourceSession.getBranch());
        tenantGuard.assertBranch(targetSession.getBranch());
        if (sourceSession.isTakeaway() || targetSession.isTakeaway()) {
            throw new ConflictException("Đơn mang về không thể gộp bàn.");
        }

        if (!java.util.Objects.equals(
                sourceSession.getBranch().getId(), targetSession.getBranch().getId())) {
            throw new InvalidParamException("Không thể gộp hai bàn thuộc hai chi nhánh khác nhau");
        }

        if (sourceSession.getStatus() == TableSessionStatus.CLOSED
                || sourceSession.getStatus() == TableSessionStatus.CANCELLED
                || sourceSession.getStatus() == TableSessionStatus.MERGED) {
            throw new ConflictException(
                    "Phiên nguồn không còn hoạt động để gộp (trạng thái: "
                            + sourceSession.getStatus()
                            + ")");
        }

        if (targetSession.getStatus() == TableSessionStatus.CLOSED
                || targetSession.getStatus() == TableSessionStatus.CANCELLED
                || targetSession.getStatus() == TableSessionStatus.MERGED) {
            throw new ConflictException(
                    "Phiên đích không còn hoạt động (trạng thái: "
                            + targetSession.getStatus()
                            + ")");
        }

        // BR-26: Kiểm tra snapshot VAT và phí dịch vụ
        BigDecimal sourceVat =
                sourceSession.getVatRateAtOpen() != null
                        ? sourceSession.getVatRateAtOpen()
                        : BigDecimal.ZERO;
        BigDecimal targetVat =
                targetSession.getVatRateAtOpen() != null
                        ? targetSession.getVatRateAtOpen()
                        : BigDecimal.ZERO;
        BigDecimal sourceServiceFee =
                sourceSession.getServiceFeeRateAtOpen() != null
                        ? sourceSession.getServiceFeeRateAtOpen()
                        : BigDecimal.ZERO;
        BigDecimal targetServiceFee =
                targetSession.getServiceFeeRateAtOpen() != null
                        ? targetSession.getServiceFeeRateAtOpen()
                        : BigDecimal.ZERO;

        if (sourceVat.compareTo(targetVat) != 0
                || sourceServiceFee.compareTo(targetServiceFee) != 0) {
            throw new ConflictException(
                    "Không thể gộp bàn vì hai bàn có bộ tỷ lệ VAT hoặc phí dịch vụ khác nhau (BR-26)");
        }

        // Chuyển toàn bộ Order từ sourceSession sang targetSession
        List<Order> sourceOrders = orderRepository.findByTableSessionId(sourceSession.getId());
        for (Order order : sourceOrders) {
            order.setTableSession(targetSession);
            orderRepository.save(order);
        }

        // Cập nhật OrderDetail nếu có liên kết trực tiếp table_session_id
        List<OrderDetail> sourceDetails =
                orderDetailRepository.findByTableSessionId(sourceSession.getId());
        for (OrderDetail detail : sourceDetails) {
            detail.setTableSession(targetSession);
            orderDetailRepository.save(detail);
        }

        // Cộng dồn số lượng khách (guestCount)
        int sourceGuestCount =
                sourceSession.getGuestCount() != null ? sourceSession.getGuestCount() : 0;
        int targetGuestCount =
                targetSession.getGuestCount() != null ? targetSession.getGuestCount() : 0;
        targetSession.setGuestCount(targetGuestCount + sourceGuestCount);

        // Đổi trạng thái sourceSession sang MERGED (BR-28), gắn mergedIntoSession
        sourceSession.setStatus(TableSessionStatus.MERGED);
        sourceSession.setMergedIntoSession(targetSession);
        sourceSession.setClosedAt(LocalDateTime.now());
        tableSessionRepository.save(sourceSession);
        orderNotificationService.notifySessionUpdated(sourceSession);

        // Trả bàn nguồn về AVAILABLE
        DiningTable sourceTable = sourceSession.getTable();
        if (sourceTable != null) {
            sourceTable.setStatus(DiningTableStatus.AVAILABLE);
            diningTableRepository.save(sourceTable);
        }

        // Đảm bảo bàn đích vẫn OCCUPIED
        DiningTable targetTable = targetSession.getTable();
        if (targetTable != null && targetTable.getStatus() != DiningTableStatus.OCCUPIED) {
            targetTable.setStatus(DiningTableStatus.OCCUPIED);
            diningTableRepository.save(targetTable);
        }

        TableSession savedTarget = tableSessionRepository.save(targetSession);
        orderNotificationService.notifySessionUpdated(savedTarget);

        auditLogService.record(
                AuditEvent.builder()
                        .entityType("TableSession")
                        .entityId(sourceSession.getId())
                        .actionType(AuditAction.STATUS_CHANGE)
                        .branchId(
                                sourceSession.getBranch() != null
                                        ? sourceSession.getBranch().getId()
                                        : null)
                        .valueBefore(
                                "status="
                                        + TableSessionStatus.SERVING
                                        + ", table="
                                        + (sourceTable != null ? sourceTable.getName() : ""))
                        .valueAfter(
                                "status="
                                        + TableSessionStatus.MERGED
                                        + ", mergedInto="
                                        + targetSession.getId())
                        .reason(
                                "Gộp bàn "
                                        + (sourceTable != null ? sourceTable.getName() : "")
                                        + " vào bàn "
                                        + (targetTable != null ? targetTable.getName() : ""))
                        .build());

        return buildResponse(savedTarget);
    }

    private TableSession findSession(Long id) {
        return tableSessionRepository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Không tìm thấy phiên bàn với ID: " + id));
    }

    private DiningTable findTable(Long tableId) {
        DiningTable table =
                diningTableRepository
                        .findForGuestAdmission(tableId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy bàn với ID: " + tableId));
        if (table.getStatus() == DiningTableStatus.INACTIVE) {
            throw new ConflictException("Bàn đã ngừng sử dụng.");
        }
        return table;
    }

    private TableSessionResponse buildResponse(TableSession session) {
        TableSessionResponse response = tableSessionMapper.toResponse(session);
        response.setCanAddTakeawayItems(
                session.canAddTakeawayItems(
                        session.isTakeaway()
                                && session.getStatus() == TableSessionStatus.WAITING_FOR_PAY
                                && invoiceRepository.existsByTableSessionId(session.getId())));
        int orderCount = (int) orderRepository.countByTableSessionId(session.getId());
        response.setOrderCount(orderCount);
        return response;
    }
}
