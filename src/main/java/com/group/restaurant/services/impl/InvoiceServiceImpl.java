package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.invoice.InvoiceCreateRequest;
import com.group.restaurant.dtos.response.invoice.InvoiceListResponse;
import com.group.restaurant.dtos.response.invoice.InvoiceResponse;
import com.group.restaurant.dtos.response.invoice.QrCodeResponse;
import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.InvoiceMapper;
import com.group.restaurant.mappers.SoftDeleteSafe;
import com.group.restaurant.models.*;
import com.group.restaurant.repositories.*;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.InvoiceService;
import com.group.restaurant.services.OrderNotificationService;
import com.group.restaurant.services.SepayTransfer;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.services.billing.BillBreakdown;
import com.group.restaurant.services.billing.BillCalculator;
import com.group.restaurant.services.billing.BillCalculator.PromotionSelection;
import com.group.restaurant.services.einvoice.EInvoiceException;
import com.group.restaurant.services.einvoice.EInvoiceResult;
import com.group.restaurant.services.einvoice.InvoiceProvider;
import com.group.restaurant.services.guest.GuestSessionGuard;
import com.group.restaurant.services.promotion.PromotionEngine;
import com.group.restaurant.utils.TenantGuard;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceServiceImpl implements InvoiceService {

    private static final String ISSUED_AUDIT_PREFIX = "status=" + InvoiceStatus.ISSUED + ";";

    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;
    private final TableSessionRepository tableSessionRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ManualDiscountRepository manualDiscountRepository;
    private final PaymentRepository paymentRepository;
    private final DiningTableRepository diningTableRepository;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceProvider invoiceProvider;
    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;
    private final TenantGuard tenantGuard;
    private final PromotionEngine promotionEngine;
    private final PromotionRepository promotionRepository;
    private final BillCalculator billCalculator;
    private final OrderNotificationService orderNotificationService;
    private final SepayTransfer sepay;
    private final GuestSessionGuard guestSessionGuard;

    @Override
    public InvoiceResponse createInvoice(InvoiceCreateRequest request) {
        if (request.getPromotionId() != null) {
            throw new InvalidParamException("Khuyến mãi hóa đơn được hệ thống tự áp dụng.");
        }
        Branch branch =
                branchRepository
                        .findById(request.getBranchId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy chi nhánh với ID: "
                                                        + request.getBranchId()));
        // branchId đến từ body của client — phải đối chiếu với nhà hàng của người đăng nhập.
        tenantGuard.assertBranch(branch);

        TableSession session =
                tableSessionRepository
                        .findForUpdate(request.getTableSessionId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy phiên làm việc với ID: "
                                                        + request.getTableSessionId()));
        tenantGuard.assertBranchAssigned(session.getBranch().getId());
        if (session.isTakeaway()) {
            tenantGuard.assertBranchAssigned(
                    session.getBranch().getId(),
                    TenantGuard.ROLE_CASHIER,
                    TenantGuard.ROLE_BRANCH_MANAGER,
                    TenantGuard.ROLE_MANAGER);
        }
        if (!branch.getId().equals(session.getBranch().getId())) {
            throw new ConflictException("Chi nhánh lập hóa đơn phải trùng với chi nhánh của đơn.");
        }

        // Phiên phải ở WAITING_FOR_PAY mới được tạo hóa đơn
        if (session.getStatus() != TableSessionStatus.WAITING_FOR_PAY) {
            throw new ConflictException(
                    "Phiên bàn phải ở trạng thái CHỜ THANH TOÁN mới có thể lập hóa đơn. Trạng thái hiện tại: "
                            + session.getStatus());
        }

        // Nếu hóa đơn cho phiên làm việc này đã tồn tại
        Optional<Invoice> existingOpt = invoiceRepository.findBySessionForPayment(session.getId());
        Invoice existingInvoice = existingOpt.orElse(null);
        if (existingInvoice != null
                && (existingInvoice.getStatus() != InvoiceStatus.PENDING
                        || existingInvoice.getQuoteSharedAt() != null
                        || paymentRepository.sumAmountByInvoiceId(existingInvoice.getId()).signum()
                                > 0)) {
            return buildInvoiceResponse(existingInvoice);
        }

        // Lấy tất cả dòng món của phiên
        List<OrderDetail> details = orderDetailRepository.findByTableSessionId(session.getId());

        if (details.isEmpty()) {
            throw new InvalidParamException("Phiên làm việc chưa có món ăn nào để lập hóa đơn");
        }

        // Chặn nếu còn dòng CHỜ CÂN (FR-PAY-01)
        boolean hasPendingWeight =
                details.stream().anyMatch(d -> d.getStatus() == OrderDetailStatus.PENDING_WEIGHT);
        if (hasPendingWeight) {
            throw new ConflictException(
                    "Không thể lập hóa đơn khi còn món đang chờ cân. Vui lòng cân và xác nhận tất cả các món bán theo cân trước.");
        }

        // Toàn bộ phép tính tiền nằm ở BillCalculator (BR-13) — KHÔNG tính rải rác ở đây nữa.
        // Thu ngân có thể chọn KM đơn (promotionId); không hợp lệ thì engine tự chọn.
        List<Order> orders = orderRepository.findByTableSessionId(session.getId());
        Order primaryOrder = orders.isEmpty() ? null : orders.get(orders.size() - 1);
        Long existingInvoiceId = existingInvoice != null ? existingInvoice.getId() : null;
        List<ManualDiscount> manualDiscounts =
                billCalculator.loadManualDiscounts(session.getId(), existingInvoiceId);

        BillBreakdown bill =
                billCalculator.calculate(
                        session,
                        branch,
                        details,
                        manualDiscounts,
                        PromotionSelection.auto(
                                reservedPromotionId(existingInvoice), java.util.Set.of()));

        SavedBill result =
                saveComputedInvoice(
                        existingInvoice,
                        session,
                        branch,
                        details,
                        manualDiscounts,
                        bill,
                        request.getInvoiceNote(),
                        primaryOrder);
        Invoice savedInvoice = result.invoice();
        bill = result.bill();

        auditLogService.record(
                AuditEvent.builder()
                        .entityType("Invoice")
                        .entityId(savedInvoice.getId())
                        .actionType(AuditAction.CREATE)
                        .branchId(branch.getId())
                        .restaurantId(branch.getRestaurant().getId())
                        .valueAfter(describeForAudit(savedInvoice.getInvoiceNumber(), bill))
                        .reason("Lập hóa đơn cho phiên bàn #" + session.getId())
                        .build());

        return buildInvoiceResponse(savedInvoice);
    }

    @Override
    public void recalculatePendingInvoice(Long invoiceId) {
        recalculatePendingInvoice(invoiceId, false);
    }

    @Override
    public void recalculateAfterItemRemoval(Long invoiceId) {
        recalculatePendingInvoice(invoiceId, true);
    }

    private void recalculatePendingInvoice(Long invoiceId, boolean itemsChanged) {
        Invoice invoice = findInvoiceById(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            return; // BR-09: PAID / ISSUED bất biến; CANCELLED không còn ý nghĩa
        }
        TableSession session = invoice.getTableSession();
        if (session == null || session.getStatus() != TableSessionStatus.WAITING_FOR_PAY) {
            return; // phiên đã mở lại — hóa đơn sẽ được lập lại đầy đủ bởi createInvoice
        }
        Branch branch = invoice.getBranch();
        List<OrderDetail> details = orderDetailRepository.findByTableSessionId(session.getId());
        List<ManualDiscount> manualDiscounts =
                billCalculator.loadManualDiscounts(session.getId(), invoice.getId());

        Long promoId = reservedPromotionId(invoice);
        BillBreakdown current = billCalculator.describeInvoice(invoice, details);
        PromotionSelection selection =
                itemsChanged
                        ? PromotionSelection.auto(promoId, java.util.Set.of())
                        : PromotionSelection.locked(
                                promoId,
                                current.getOrderPromotionName(),
                                current.getOrderPromotionDiscount());
        if (itemsChanged) invoice.setDiscountApproval(null);
        BillBreakdown bill =
                billCalculator.calculate(session, branch, details, manualDiscounts, selection);

        BigDecimal oldGrandTotal = invoice.getGrandTotal();
        SavedBill result =
                saveComputedInvoice(
                        invoice, session, branch, details, manualDiscounts, bill, null, null);
        Invoice saved = result.invoice();

        auditLogService.record(
                AuditEvent.builder()
                        .entityType("Invoice")
                        .entityId(saved.getId())
                        .actionType(AuditAction.UPDATE)
                        .branchId(branch != null ? branch.getId() : null)
                        .restaurantId(
                                branch != null && branch.getRestaurant() != null
                                        ? branch.getRestaurant().getId()
                                        : null)
                        .valueBefore("grandTotal=" + oldGrandTotal)
                        .valueAfter(describeForAudit(saved.getInvoiceNumber(), result.bill()))
                        .reason(
                                itemsChanged
                                        ? "Tính lại hóa đơn sau khi bỏ món tính nhầm"
                                        : "Tính lại hóa đơn PENDING do chiết khấu thủ công thay đổi")
                        .build());
    }

    /**
     * Hóa đơn đã lưu kèm breakdown thực sự được ghi (có thể khác breakdown truyền vào nếu KM đơn
     * vừa hết lượt và phải tính lại không KM đơn).
     */
    private record SavedBill(Invoice invoice, BillBreakdown bill) {}

    private Long reservedPromotionId(Invoice invoice) {
        return invoice != null && invoice.getPromotion() != null
                ? invoice.getPromotion().getId()
                : null;
    }

    /**
     * Ghi hóa đơn từ kết quả tính, xử lý lượt KM đơn (trừ lượt KM mới, hoàn lượt KM cũ bị thay)
     * và gắn chiết khấu tay chưa có hóa đơn vào hóa đơn này.
     */
    private SavedBill saveComputedInvoice(
            Invoice existing,
            TableSession session,
            Branch branch,
            List<OrderDetail> details,
            List<ManualDiscount> manualDiscounts,
            BillBreakdown computed,
            String invoiceNote,
            Order primaryOrder) {
        BillBreakdown bill = computed;
        Long oldPromoId =
                existing != null && existing.getPromotion() != null
                        ? existing.getPromotion().getId()
                        : null;
        Long newPromoId = bill.getOrderPromotionId();

        // BR-23: chỉ trừ lượt khi KM đơn MỚI khác KM đã trừ trước đó trên chính hóa đơn này.
        java.util.Set<Long> exhaustedIds = new java.util.HashSet<>();
        while (newPromoId != null
                && !newPromoId.equals(oldPromoId)
                && !promotionEngine.incrementUsageCount(newPromoId)) {
            exhaustedIds.add(newPromoId);
            bill =
                    billCalculator.calculate(
                            session,
                            branch,
                            details,
                            manualDiscounts,
                            PromotionSelection.auto(oldPromoId, exhaustedIds));
            newPromoId = bill.getOrderPromotionId();
        }
        // KM cũ đã bị thay thế hoặc không còn áp → hoàn lượt
        if (oldPromoId != null && !oldPromoId.equals(newPromoId)) {
            promotionEngine.decrementUsageCount(oldPromoId);
        }

        Promotion promotionEntity;
        if (newPromoId == null) {
            promotionEntity = null;
        } else if (newPromoId.equals(oldPromoId)) {
            promotionEntity = existing.getPromotion();
        } else {
            promotionEntity = promotionRepository.findById(newPromoId).orElse(null);
        }

        Invoice invoiceToSave;
        if (existing != null) {
            invoiceToSave = existing;
            if (invoiceNote != null) {
                invoiceToSave.setInvoiceNote(invoiceNote);
            }
        } else {
            invoiceToSave =
                    Invoice.builder()
                            .branch(branch)
                            .tableSession(session)
                            .order(primaryOrder)
                            .invoiceNumber(generateInvoiceNumber(branch))
                            .invoiceNote(invoiceNote)
                            .status(InvoiceStatus.PENDING)
                            .build();
        }
        invoiceToSave.setSubTotalBeforeDiscount(bill.getGrossSubTotal());
        invoiceToSave.setTotalPromotionDiscount(bill.getTotalPromotionDiscount());
        invoiceToSave.setPromotion(promotionEntity);
        invoiceToSave.setTotalManualDiscount(bill.getManualDiscount());
        invoiceToSave.setServiceFee(bill.getServiceFee());
        invoiceToSave.setVatAmount(bill.getVatAmount());
        invoiceToSave.setGrandTotal(bill.getGrandTotal());

        Invoice saved = invoiceRepository.save(invoiceToSave);

        // Gắn hóa đơn vào các chiết khấu tay mới tạo theo order
        for (ManualDiscount md : manualDiscounts) {
            if (md.getInvoice() == null) {
                md.setInvoice(saved);
                manualDiscountRepository.save(md);
            }
        }

        orderNotificationService.notifySessionUpdated(saved.getTableSession());
        return new SavedBill(saved, bill);
    }

    private String describeForAudit(String invoiceNumber, BillBreakdown b) {
        return "invoiceNumber="
                + invoiceNumber
                + "; grossSubTotal="
                + b.getGrossSubTotal()
                + "; itemPromoDiscount="
                + b.getItemPromotionDiscount()
                + "; orderPromoDiscount="
                + b.getOrderPromotionDiscount()
                + (b.getOrderPromotionName() != null
                        ? " (promo: " + b.getOrderPromotionName() + ")"
                        : "")
                + "; manualDiscount="
                + b.getManualDiscount()
                + "; serviceFee="
                + b.getServiceFee()
                + " ("
                + b.getServiceFeeRate()
                + "%)"
                + "; vat="
                + b.getVatAmount()
                + " ("
                + b.getVatRate()
                + "%"
                + (b.isVatOnServiceFee() ? ", trên cả phí DV" : ", không trên phí DV")
                + ")"
                + "; grandTotal="
                + b.getGrandTotal();
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Long id) {
        return buildInvoiceResponse(findInvoiceById(id));
    }

    /**
     * Đọc hóa đơn của một phiên bàn. CHỈ ĐỌC.
     *
     * Trước đây phương thức này tự lập hóa đơn và đẩy phiên SERVING sang WAITING_FOR_PAY
     * (be-issues mục 17). Đây là endpoint mà màn Thanh toán QR của khách poll mỗi 1 giây,
     * lại nằm trong danh sách GET công khai, nên chỉ cần mở màn đó — hoặc gọi thẳng URL mà
     * không cần đăng nhập — là bàn bị khóa khỏi trạng thái đang phục vụ và một hóa đơn
     * ngoài ý muốn được sinh ra. Việc lập hóa đơn nay chỉ do POST /invoices (Thu ngân /
     * QL chi nhánh) thực hiện — Bảng 4.2 "Thu tiền, đóng bàn".
     */
    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByTableSession(Long tableSessionId) {
        return invoiceRepository
                .findByTableSessionId(tableSessionId)
                .map(this::buildInvoiceResponse)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Chưa có hóa đơn cho phiên làm việc ID: "
                                                + tableSessionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceListResponse> getInvoicesByBranch(Long branchId, InvoiceStatus status) {
        if (!branchRepository.existsById(branchId)) {
            throw new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + branchId);
        }
        // BR-18 — Bảng 4.2 "Thu tiền, đóng bàn" / "Phát hành hóa đơn điện tử": Thu ngân và
        // QL chi nhánh. Phục vụ KHÔNG được xem danh sách hoá đơn của chi nhánh.
        tenantGuard.assertBranchAssigned(
                branchId, TenantGuard.ROLE_CASHIER, TenantGuard.ROLE_BRANCH_MANAGER);
        List<Invoice> invoices =
                (status != null)
                        ? invoiceRepository.findByBranchIdAndStatus(branchId, status)
                        : invoiceRepository.findByBranchId(branchId);

        Map<Long, List<AuditLog>> issuanceLogs =
                invoices.stream().anyMatch(InvoiceServiceImpl::hasIssuedInvoice)
                        ? auditLogRepository
                                .findInvoiceIssuanceLogs(
                                        branchId, AuditAction.ISSUE_EINVOICE, ISSUED_AUDIT_PREFIX)
                                .stream()
                                .collect(Collectors.groupingBy(AuditLog::getEntityId))
                        : Map.of();

        return invoices.stream()
                .map(
                        invoice -> {
                            InvoiceListResponse response = invoiceMapper.toListResponse(invoice);
                            if (hasIssuedInvoice(invoice)) {
                                String prefix = issueResultPrefix(invoice);
                                issuanceLogs.getOrDefault(invoice.getId(), List.of()).stream()
                                        .filter(log -> log.getValueAfter().startsWith(prefix))
                                        .findFirst()
                                        .ifPresent(
                                                log -> response.setIssuerName(
                                                        SoftDeleteSafe.get(
                                                                log.getPerformedBy(), Account::getFullName)));
                            }
                            return response;
                        })
                .toList();
    }

    @Override
    public void cancelInvoice(Long id) {
        Invoice invoice =
                invoiceRepository
                        .findForPayment(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy hóa đơn với ID: " + id));
        tenantGuard.assertBranch(invoice.getBranch());
        // BR-09: Hóa đơn đã phát hành là BẤT BIẾN — cả PAID lẫn ISSUED đều không hủy được
        if (invoice.getStatus() == InvoiceStatus.PAID
                || invoice.getStatus() == InvoiceStatus.ISSUED) {
            throw new ConflictException(
                    "Không thể hủy hóa đơn đã thanh toán hoặc đã phát hành điện tử (BR-09)");
        }
        InvoiceStatus previousStatus = invoice.getStatus();
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoiceRepository.save(invoice);
        orderNotificationService.notifySessionUpdated(invoice.getTableSession());

        auditLogService.record(
                AuditEvent.builder()
                        .entityType("Invoice")
                        .entityId(invoice.getId())
                        .actionType(AuditAction.CANCEL)
                        .branchId(invoice.getBranch() != null ? invoice.getBranch().getId() : null)
                        .valueBefore("status=" + previousStatus)
                        .valueAfter("status=" + InvoiceStatus.CANCELLED)
                        .reason(
                                "Hủy hóa đơn "
                                        + invoice.getInvoiceNumber()
                                        + " (grandTotal="
                                        + invoice.getGrandTotal()
                                        + ")")
                        .build());
    }

    // Xóa hóa đơn bị cấm — chỉ cho cancel
    // deleteInvoice() đã bị xóa khỏi InvoiceService interface

    @Override
    public InvoiceResponse issueEInvoice(Long invoiceId) {
        invoiceRepository.findSessionId(invoiceId).ifPresent(tableSessionRepository::findForUpdate);
        Invoice invoice =
                invoiceRepository
                        .findForPayment(invoiceId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy hóa đơn với ID: " + invoiceId));
        tenantGuard.assertBranch(invoice.getBranch());
        if (invoice.getStatus() == InvoiceStatus.ISSUED) return buildInvoiceResponse(invoice);

        // Chỉ cho phát hành khi đã PAID
        if (invoice.getStatus() != InvoiceStatus.PAID) {
            throw new ConflictException(
                    "Chỉ có thể phát hành hóa đơn điện tử cho hóa đơn đã thanh toán đủ. "
                            + "Trạng thái hiện tại: "
                            + invoice.getStatus());
        }

        // Resolve mã số thuế theo : ưu tiên chi nhánh, nếu không có thì dùng nhà hàng
        Branch branch = invoice.getBranch();
        String taxCode =
                (branch.getTaxCode() != null && !branch.getTaxCode().isBlank())
                        ? branch.getTaxCode()
                        : branch.getRestaurant().getTaxCode();

        // Tăng attempt count trước khi gọi provider (để ghi nhận dù thành công hay thất bại)
        invoice.setEInvoiceAttemptCount(invoice.getEInvoiceAttemptCount() + 1);
        invoiceRepository.saveAndFlush(invoice);

        // Gọi provider — có thể ném EInvoiceException nếu thất bại
        EInvoiceResult result;
        try {
            result = invoiceProvider.issue(invoice);
        } catch (EInvoiceException ex) {
            // FR-PAY-04: Phát hành thất bại → CHẶN đóng bàn, Thu ngân thử lại
            log.error(
                    "[FR-PAY-04] Phát hành HĐĐT thất bại cho invoice #{} qua provider '{}': {}",
                    invoiceId,
                    ex.getProviderName(),
                    ex.getMessage());
            // Ghi cả lần thất bại: FR-PAY-04 chặn đóng bàn, cần vết để đối soát vì sao bàn treo.
            auditLogService.record(
                    AuditEvent.builder()
                            .entityType("Invoice")
                            .entityId(invoiceId)
                            .actionType(AuditAction.ISSUE_EINVOICE)
                            .branchId(branch.getId())
                            .valueAfter(
                                    "status=FAILED; attempt="
                                            + invoice.getEInvoiceAttemptCount()
                                            + "; provider="
                                            + ex.getProviderName())
                            .reason("Phát hành HĐĐT thất bại: " + ex.getMessage())
                            .build());
            throw new ConflictException(
                    "Phát hành hóa đơn điện tử thất bại (lần thử #"
                            + invoice.getEInvoiceAttemptCount()
                            + "): "
                            + ex.getMessage()
                            + ". Vui lòng thử lại.");
        }

        // Phát hành thành công — cập nhật invoice với đầy đủ thông tin HĐĐT
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setEInvoiceRef(result.getRef());
        invoice.setEInvoiceIssuedAt(
                result.getIssuedAt() != null ? result.getIssuedAt() : LocalDateTime.now());
        invoice.setTaxCodeUsed(taxCode);
        invoice.setEInvoiceProvider(result.getProviderName());
        // saveAndFlush đảm bảo các field HĐĐT được ghi xuống DB ngay trong transaction này
        invoiceRepository.saveAndFlush(invoice);

        // Đóng session và bàn sau khi HĐĐT phát hành xong (FR-PAY-04: thành công mới CLOSED)
        TableSession session = invoice.getTableSession();
        if (session != null
                && session.getStatus() != TableSessionStatus.CLOSED
                && session.getStatus() != TableSessionStatus.CANCELLED) {
            session.setStatus(TableSessionStatus.CLOSED);
            session.setClosedAt(LocalDateTime.now());
            tableSessionRepository.save(session);

            DiningTable table = session.getTable();
            if (table != null) {
                table.setStatus(DiningTableStatus.AVAILABLE);
                diningTableRepository.save(table);
            }
        }

        log.info(
                "[FR-PAY-04] Phát hành HĐĐT thành công: invoice #{} → ref='{}', provider='{}', taxCode='{}'",
                invoiceId,
                result.getRef(),
                result.getProviderName(),
                taxCode);

        auditLogService.recordInTransaction(
                AuditEvent.builder()
                        .entityType("Invoice")
                        .entityId(invoiceId)
                        .actionType(AuditAction.ISSUE_EINVOICE)
                        .branchId(branch.getId())
                        .valueBefore("status=" + InvoiceStatus.PAID)
                        .valueAfter(
                                "status="
                                        + InvoiceStatus.ISSUED
                                        + "; ref="
                                        + result.getRef()
                                        + "; provider="
                                        + result.getProviderName()
                                        + "; taxCode="
                                        + taxCode)
                        .reason("Phát hành HĐĐT cho hóa đơn " + invoice.getInvoiceNumber())
                        .build());

        orderNotificationService.notifySessionUpdated(session);
        // Re-fetch từ DB sau saveAndFlush để response trả về đúng data đã persist
        Invoice refreshed = findInvoiceById(invoiceId);
        return buildInvoiceResponse(refreshed);
    }

    @Override
    @Transactional(readOnly = true)
    public QrCodeResponse generateQrCode(Long invoiceId) {
        Invoice invoice = findInvoiceById(invoiceId);

        // Tính số tiền còn lại
        BigDecimal totalPaid = paymentRepository.sumAmountByInvoiceId(invoiceId);
        if (totalPaid == null) totalPaid = BigDecimal.ZERO;
        BigDecimal remainingAmount = invoice.getGrandTotal().subtract(totalPaid);
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0
                || invoice.getStatus() == InvoiceStatus.PAID
                || invoice.getStatus() == InvoiceStatus.ISSUED) {
            return QrCodeResponse.builder()
                    .invoiceId(invoice.getId())
                    .invoiceNumber(invoice.getInvoiceNumber())
                    .remainingAmount(BigDecimal.ZERO)
                    .grandTotal(invoice.getGrandTotal())
                    .totalPaid(totalPaid)
                    .bankName("")
                    .accountNo("")
                    .accountName("")
                    .qrImageUrl("")
                    .transferContent("")
                    .build();
        }

        // Tài khoản ngân hàng của CHÍNH chi nhánh này — KHÔNG có fallback.
        //
        // Trước đây chi nhánh chưa cấu hình thì BE thế bằng số tài khoản cố định
        // 0071000988888 / "Nhà hàng Phở Việt". Hậu quả: khách quét QR và chuyển tiền
        // thật vào tài khoản KHÔNG phải của nhà hàng. Thà chặn còn hơn thu nhầm tiền.
        Branch branch = invoice.getBranch();
        String rawAccountNo = branch != null ? branch.getBankAccountNo() : null;
        String rawBankName = branch != null ? branch.getBankName() : null;
        if (rawAccountNo == null
                || rawAccountNo.isBlank()
                || rawBankName == null
                || rawBankName.isBlank()) {
            throw new ConflictException("Chi nhánh chưa cấu hình tài khoản ngân hàng");
        }
        String bankName = rawBankName;
        String accountName =
                (branch.getName() != null && !branch.getName().isBlank())
                        ? branch.getName()
                        : bankName;

        String bankAccountNo = rawAccountNo.replaceAll("\\s+", "");

        // Nội dung chuyển khoản (bỏ dấu gạch để gọn)
        boolean automaticVerification = sepay.enabledFor(branch);
        String transferContent =
                automaticVerification
                        ? sepay.invoiceCode(invoice.getId())
                        : "TT " + invoice.getInvoiceNumber().replace("-", "");

        // Sinh URL QR VietQR public API
        String qrImageUrl =
                buildVietQrUrl(
                        bankName, bankAccountNo, remainingAmount, transferContent, accountName);

        return QrCodeResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .remainingAmount(remainingAmount)
                .grandTotal(invoice.getGrandTotal())
                .totalPaid(totalPaid)
                .bankName(bankName)
                .accountNo(bankAccountNo)
                .accountName(accountName)
                .qrImageUrl(qrImageUrl)
                .transferContent(transferContent)
                .automaticVerification(automaticVerification)
                .build();
    }

    /**
     * Lấy mã QR chuyển khoản theo phiên bàn. CHỈ ĐỌC.
     *
     * Trước đây phương thức này tự lập hóa đơn và đẩy phiên SERVING sang WAITING_FOR_PAY
     * (be-issues mục 17) — một GET công khai làm đổi trạng thái nghiệp vụ. Nay hóa đơn
     * phải do Thu ngân lập trước (POST /invoices); chưa có thì trả 409 để màn của khách
     * hiện "Đang chờ thu ngân lập hóa đơn" và tiếp tục poll.
     *
     * Hóa đơn đã hủy coi như chưa có: không sinh QR cho nó.
     */
    @Override
    @Transactional(readOnly = true)
    public QrCodeResponse generateQrCodeByTableSession(Long tableSessionId) {
        Invoice invoice =
                invoiceRepository
                        .findByTableSessionId(tableSessionId)
                        .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED)
                        .orElseThrow(
                                () ->
                                        new ConflictException(
                                                "Chưa có hóa đơn, vui lòng chờ thu ngân"));
        return generateQrCode(invoice.getId());
    }

    @Override
    public void reportGuestQrOpened(
            Long tableSessionId, String guestToken, BigDecimal expectedTotal) {
        TableSession session =
                tableSessionRepository
                        .findForUpdate(tableSessionId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Không tìm thấy phiên bàn."));
        guestSessionGuard.requireGuest(guestToken, session, true);
        Invoice invoice = invoiceRepository.findBySessionForPayment(tableSessionId).orElse(null);
        if (invoice == null
                || session.getStatus() != TableSessionStatus.WAITING_FOR_PAY
                || invoice.getStatus() != InvoiceStatus.PENDING
                || invoice.getGrandTotal().signum() <= 0
                || (expectedTotal != null
                        && expectedTotal.compareTo(invoice.getGrandTotal()) != 0)) {
            throw new ConflictException("Tạm tính đã thay đổi. Vui lòng tải lại mã QR.");
        }
        if (invoice.getQuoteSharedAt() == null) invoice.setQuoteSharedAt(LocalDateTime.now());
        orderNotificationService.notifyPaymentQrOpened(session);
    }

    @Override
    public void shareQuote(Long invoiceId, BigDecimal expectedTotal) {
        Invoice invoice =
                invoiceRepository
                        .findForPayment(invoiceId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Không tìm thấy hóa đơn."));
        tenantGuard.assertBranchAssigned(
                invoice.getBranch().getId(),
                TenantGuard.ROLE_CASHIER,
                TenantGuard.ROLE_BRANCH_MANAGER,
                TenantGuard.ROLE_MANAGER);
        if (invoice.getStatus() != InvoiceStatus.PENDING
                || invoice.getTableSession().getStatus() != TableSessionStatus.WAITING_FOR_PAY
                || expectedTotal.compareTo(invoice.getGrandTotal()) != 0) {
            throw new ConflictException(
                    "Tạm tính đã thay đổi. Vui lòng tải lại trước khi đưa cho khách.");
        }
        if (invoice.getQuoteSharedAt() == null) {
            invoice.setQuoteSharedAt(LocalDateTime.now());
            orderNotificationService.notifySessionUpdated(invoice.getTableSession());
        }
    }

    private String buildVietQrUrl(
            String bankName,
            String accountNo,
            BigDecimal amount,
            String addInfo,
            String accountName) {
        String bankId = normalizeBankId(bankName);
        String baseAccountNo = (accountNo != null) ? accountNo : "0000000000";

        return "https://img.vietqr.io/image/"
                + bankId
                + "-"
                + baseAccountNo
                + "-compact2.png"
                + "?amount="
                + amount.setScale(0, java.math.RoundingMode.HALF_UP).longValue()
                + "&addInfo="
                + encode(addInfo)
                + "&accountName="
                + encode(accountName);
    }

    private String normalizeBankId(String bankName) {
        if (bankName == null) return "VCB";
        return switch (bankName.toUpperCase().trim()) {
            case "VCB", "VIETCOMBANK" -> "VCB";
            case "TCB", "TECHCOMBANK" -> "TCB";
            case "MB", "MBBANK", "MB BANK" -> "MB";
            case "ACB" -> "ACB";
            case "VPB", "VPBANK" -> "VPB";
            case "BIDV" -> "BIDV";
            case "CTG", "VIETINBANK" -> "CTG";
            case "AGR", "AGRIBANK" -> "AGR";
            case "TPB", "TPBANK" -> "TPB";
            case "STB", "SACOMBANK" -> "STB";
            case "OCB" -> "OCB";
            case "MSB" -> "MSB";
            case "SHB" -> "SHB";
            default -> bankName.toUpperCase();
        };
    }

    private String encode(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private Invoice findInvoiceById(Long id) {
        return invoiceRepository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Không tìm thấy hóa đơn với ID: " + id));
    }

    /**
     *  Số hóa đơn riêng theo chi nhánh
     */
    private String generateInvoiceNumber(Branch branch) {
        String branchCode =
                branch.getCode() != null ? branch.getCode().toUpperCase() : "B" + branch.getId();
        String date =
                java.time.LocalDate.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = invoiceRepository.countByBranchId(branch.getId()) + 1;
        String invoiceNumber = branchCode + "-" + date + "-" + String.format("%04d", seq);

        // Đảm bảo không trùng (edge case)
        while (invoiceRepository.existsByInvoiceNumber(invoiceNumber)) {
            seq++;
            invoiceNumber = branchCode + "-" + date + "-" + String.format("%04d", seq);
        }
        return invoiceNumber;
    }

    private static boolean hasIssuedInvoice(Invoice invoice) {
        return invoice.getStatus() == InvoiceStatus.ISSUED
                || invoice.getEInvoiceIssuedAt() != null
                || invoice.getEInvoiceRef() != null;
    }

    private static String issueResultPrefix(Invoice invoice) {
        String ref = invoice.getEInvoiceRef();
        return ref == null || ref.isBlank() ? ISSUED_AUDIT_PREFIX : ISSUED_AUDIT_PREFIX + " ref=" + ref + ";";
    }

    private InvoiceResponse buildInvoiceResponse(Invoice invoice) {
        InvoiceResponse response = invoiceMapper.toResponse(invoice);
        if (response.getBranchId() != null && hasIssuedInvoice(invoice)) {
            auditLogRepository
                    .findFirstByBranchIdAndEntityTypeAndEntityIdAndActionTypeAndValueAfterStartingWithOrderByIdDesc(
                            response.getBranchId(),
                            "Invoice",
                            invoice.getId(),
                            AuditAction.ISSUE_EINVOICE,
                            issueResultPrefix(invoice))
                    .ifPresent(
                            entry -> {
                                Account issuer = entry.getPerformedBy();
                                response.setEInvoiceIssuedByName(
                                        SoftDeleteSafe.get(issuer, Account::getFullName));
                                response.setEInvoiceIssuedByEmployeeCode(
                                        SoftDeleteSafe.get(issuer, Account::getEmployeeCode));
                            });
        }
        response.setQuoteShared(invoice.getQuoteSharedAt() != null);
        response.setDiscountApprovalPending(
                invoice.getDiscountApproval() != null
                        && invoice.getStatus() == InvoiceStatus.PENDING);

        BigDecimal totalPaid = paymentRepository.sumAmountByInvoiceId(invoice.getId());
        if (totalPaid == null) totalPaid = BigDecimal.ZERO;

        BigDecimal remainingAmount = invoice.getGrandTotal().subtract(totalPaid);
        if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) remainingAmount = BigDecimal.ZERO;

        response.setTotalPaid(totalPaid);
        response.setRemainingAmount(remainingAmount);

        // Breakdown đầy đủ (BR-13), suy ra từ số đã lưu của hóa đơn + dòng món của phiên.
        Long sessionId =
                invoice.getTableSession() != null ? invoice.getTableSession().getId() : null;
        List<OrderDetail> details =
                sessionId != null
                        ? orderDetailRepository.findByTableSessionId(sessionId)
                        : List.of();
        BillBreakdown b = billCalculator.describeInvoice(invoice, details);
        response.setGrossSubTotal(b.getGrossSubTotal());
        response.setItemPromotionDiscount(b.getItemPromotionDiscount());
        response.setOrderPromotionDiscount(b.getOrderPromotionDiscount());
        response.setOrderPromotionId(b.getOrderPromotionId());
        response.setOrderPromotionName(b.getOrderPromotionName());
        response.setManualDiscount(b.getManualDiscount());
        response.setAmountAfterDiscount(b.getAmountAfterDiscount());
        response.setServiceFeeRate(b.getServiceFeeRate());
        response.setVatRate(b.getVatRate());
        response.setVatOnServiceFee(b.isVatOnServiceFee());
        response.setVatBase(b.getVatBase());
        return response;
    }
}
