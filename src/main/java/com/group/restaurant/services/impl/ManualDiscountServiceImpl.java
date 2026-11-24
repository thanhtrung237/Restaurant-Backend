package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.manualDiscount.ManualDiscountAddRequest;
import com.group.restaurant.dtos.response.invoice.InvoiceResponse;
import com.group.restaurant.dtos.response.manualDiscount.ManualDiscountResponse;
import com.group.restaurant.enums.DiscountType;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.ManualDiscountMapper;
import com.group.restaurant.models.Invoice;
import com.group.restaurant.models.InvoiceDiscountApproval;
import com.group.restaurant.models.ManualDiscount;
import com.group.restaurant.models.Order;
import com.group.restaurant.repositories.InvoiceRepository;
import com.group.restaurant.repositories.ManualDiscountRepository;
import com.group.restaurant.repositories.OrderRepository;
import com.group.restaurant.repositories.PaymentRepository;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.InvoiceService;
import com.group.restaurant.services.ManualDiscountService;
import com.group.restaurant.services.OrderNotificationService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.utils.SecurityUtils;
import com.group.restaurant.utils.TenantGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ManualDiscountServiceImpl implements ManualDiscountService {

    private final ManualDiscountRepository manualDiscountRepository;
    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final ManualDiscountMapper manualDiscountMapper;
    private final AuditLogService auditLogService;
    private final TenantGuard tenantGuard;
    private final InvoiceService invoiceService;
    private final PaymentRepository paymentRepository;
    private final SecurityUtils securityUtils;
    private final OrderNotificationService orderNotificationService;

    @Override
    public ManualDiscountResponse createManualDiscount(ManualDiscountAddRequest request) {
        if (request.getInvoiceId() != null && request.getOrderId() == null) {
            Invoice invoice = lockEditableInvoice(request.getInvoiceId());
            assertQuoteNotShared(invoice);
            return manualDiscountMapper.toResponse(saveInvoiceDiscount(invoice, request));
        }

        ManualDiscount manualDiscount =
                ManualDiscount.builder()
                        .type(request.getType())
                        .value(request.getValue())
                        .reason(request.getReason())
                        .build();

        if (request.getOrderId() != null) {
            Order order =
                    orderRepository
                            .findById(request.getOrderId())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Không tìm thấy đơn hàng với ID: "
                                                            + request.getOrderId()));
            manualDiscount.setOrder(order);
        }

        if (request.getInvoiceId() != null) {
            Invoice invoice =
                    invoiceRepository
                            .findById(request.getInvoiceId())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Không tìm thấy hóa đơn với ID: "
                                                            + request.getInvoiceId()));
            manualDiscount.setInvoice(invoice);
        }

        // Chặn tạo chiết khấu lên order/hóa đơn của nhà hàng khác.
        tenantGuard.assertBranch(resolveBranchId(manualDiscount));

        // BR-09: hóa đơn đã thu đủ / đã phát hành là bất biến — không thêm chiết khấu vào nữa.
        assertInvoiceStillEditable(manualDiscount);
        assertQuoteNotSharedForLegacyDiscount(manualDiscount);

        ManualDiscount saved = saveAndAudit(manualDiscount);
        recalculateInvoiceOf(saved);
        return manualDiscountMapper.toResponse(saved);
    }

    private ManualDiscount saveAndAudit(ManualDiscount manualDiscount) {
        ManualDiscount saved = manualDiscountRepository.save(manualDiscount);

        // FR-LOG-01: chiết khấu tay là hành động dễ bị lạm dụng nhất — luôn phải có vết,
        // kèm chi nhánh để QL chi nhánh đối soát được.
        auditLogService.record(
                AuditEvent.builder()
                        .entityType("ManualDiscount")
                        .entityId(saved.getId())
                        .actionType(AuditAction.DISCOUNT)
                        .branchId(resolveBranchId(saved))
                        .valueAfter(
                                "type="
                                        + saved.getType()
                                        + "; value="
                                        + saved.getValue()
                                        + "; orderId="
                                        + (saved.getOrder() != null
                                                ? saved.getOrder().getId()
                                                : null)
                                        + "; invoiceId="
                                        + (saved.getInvoice() != null
                                                ? saved.getInvoice().getId()
                                                : null))
                        .reason(saved.getReason())
                        .build());

        return saved;
    }

    @Override
    public InvoiceResponse replaceInvoiceDiscount(
            Long invoiceId, ManualDiscountAddRequest request) {
        if (request.getOrderId() != null || request.getInvoiceId() != null) {
            throw new InvalidParamException(
                    "Chiết khấu này áp dụng cho toàn hóa đơn theo mã trên đường dẫn.");
        }
        Invoice invoice = lockEditableInvoice(invoiceId);
        validateInvoiceDiscount(invoice, request);
        if (invoice.getQuoteSharedAt() != null) {
            requestApproval(invoice, request);
        } else {
            saveInvoiceDiscount(invoice, request);
        }
        return invoiceService.getInvoiceById(invoiceId);
    }

    @Override
    public InvoiceResponse clearInvoiceDiscount(Long invoiceId) {
        Invoice invoice = lockEditableInvoice(invoiceId);
        if (invoice.getQuoteSharedAt() != null) {
            requestApproval(invoice, null);
        } else {
            removeInvoiceDiscounts(invoice);
            invoiceService.recalculatePendingInvoice(invoiceId);
        }
        return invoiceService.getInvoiceById(invoiceId);
    }

    private void validateInvoiceDiscount(Invoice invoice, ManualDiscountAddRequest request) {
        BigDecimal value = request.getValue();
        BigDecimal base =
                invoice.getSubTotalBeforeDiscount().subtract(invoice.getTotalPromotionDiscount());
        if (value.signum() <= 0
                || value.stripTrailingZeros().scale() > 0
                || (request.getType() == DiscountType.PERCENTAGE
                        && value.compareTo(BigDecimal.valueOf(100)) >= 0)
                || (request.getType() == DiscountType.FIXED_AMOUNT
                        && (value.compareTo(BigDecimal.valueOf(99_999_999)) > 0
                                || value.compareTo(base) >= 0))) {
            throw new InvalidParamException(
                    "Nhập số nguyên dương nhỏ hơn 100% hoặc nhỏ hơn tiền hàng sau khuyến mãi.");
        }
    }

    private ManualDiscount saveInvoiceDiscount(Invoice invoice, ManualDiscountAddRequest request) {
        validateInvoiceDiscount(invoice, request);
        removeInvoiceDiscounts(invoice);
        ManualDiscount saved =
                saveAndAudit(
                        ManualDiscount.builder()
                                .invoice(invoice)
                                .type(request.getType())
                                .value(request.getValue())
                                .reason(request.getReason().trim())
                                .build());
        invoiceService.recalculatePendingInvoice(invoice.getId());
        if (invoice.getGrandTotal().signum() <= 0) {
            throw new InvalidParamException("Hóa đơn cần còn số tiền phải thu sau chiết khấu.");
        }
        return saved;
    }

    @Override
    public InvoiceDiscountApproval getApproval(Long invoiceId) {
        return lockEditableInvoice(invoiceId).getDiscountApproval();
    }

    @Override
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'MANAGER')")
    public InvoiceResponse decideApproval(Long invoiceId, String requestId, boolean approved) {
        Invoice invoice = lockEditableInvoice(invoiceId);
        tenantGuard.assertBranchAssigned(
                invoice.getBranch().getId(), TenantGuard.ROLE_BRANCH_MANAGER);
        InvoiceDiscountApproval request = invoice.getDiscountApproval();
        if (request == null || !requestId.equals(request.getRequestId())) {
            throw new ConflictException("Yêu cầu đã thay đổi. Vui lòng tải lại.");
        }
        if (request.getRequestedBy().equals(securityUtils.getCurrentAccountId())) {
            throw new AccessDeniedException("Yêu cầu cần được xử lý bởi một quản lý khác.");
        }
        if (approved && request.getQuotedTotal().compareTo(invoice.getGrandTotal()) != 0) {
            throw new ConflictException(
                    "Tổng tiền đã thay đổi. Hãy từ chối yêu cầu cũ rồi kiểm tra lại.");
        }
        invoice.setDiscountApproval(null);
        if (approved) {
            if (request.getType() == null) {
                removeInvoiceDiscounts(invoice);
                invoiceService.recalculatePendingInvoice(invoiceId);
            } else {
                ManualDiscountAddRequest discount = new ManualDiscountAddRequest();
                discount.setType(request.getType());
                discount.setValue(request.getValue());
                discount.setReason(request.getReason());
                saveInvoiceDiscount(invoice, discount);
            }
        }
        recordApproval(
                invoice, request, approved ? "Duyệt chiết khấu" : "Từ chối chiết khấu", approved);
        return invoiceService.getInvoiceById(invoiceId);
    }

    private void requestApproval(Invoice invoice, ManualDiscountAddRequest discount) {
        if (invoice.getDiscountApproval() != null) {
            throw new ConflictException("Hóa đơn đã có yêu cầu chiết khấu đang chờ duyệt.");
        }
        Long requester = securityUtils.getCurrentAccountId();
        if (requester == null)
            throw new AccessDeniedException("Cần đăng nhập để gửi yêu cầu chiết khấu.");
        InvoiceDiscountApproval request =
                new InvoiceDiscountApproval(
                        java.util.UUID.randomUUID().toString(),
                        requester,
                        discount == null ? null : discount.getType(),
                        discount == null ? null : discount.getValue(),
                        discount == null
                                ? "Bỏ chiết khấu toàn hóa đơn"
                                : discount.getReason().trim(),
                        invoice.getGrandTotal());
        invoice.setDiscountApproval(request);
        recordApproval(invoice, request, "Gửi yêu cầu chiết khấu", false);
        orderNotificationService.notifyDiscountApprovalRequested(invoice.getTableSession());
    }

    private void recordApproval(
            Invoice invoice, InvoiceDiscountApproval request, String action, boolean approved) {
        auditLogService.record(
                AuditEvent.builder()
                        .entityType("Invoice")
                        .entityId(invoice.getId())
                        .branchId(invoice.getBranch().getId())
                        .actionType(AuditAction.DISCOUNT)
                        .approvedByAccountId(approved ? securityUtils.getCurrentAccountId() : null)
                        .valueBefore("quotedTotal=" + request.getQuotedTotal())
                        .valueAfter(
                                "requester="
                                        + request.getRequestedBy()
                                        + "; type="
                                        + request.getType()
                                        + "; value="
                                        + request.getValue()
                                        + "; grandTotal="
                                        + invoice.getGrandTotal())
                        .reason(action + " · " + request.getReason())
                        .build());
    }

    private void assertQuoteNotShared(Invoice invoice) {
        if (invoice.getQuoteSharedAt() != null) {
            throw new ConflictException(
                    "Tạm tính đã gửi khách. Vui lòng gửi yêu cầu chiết khấu trên màn thanh toán.");
        }
    }

    private void assertQuoteNotSharedForLegacyDiscount(ManualDiscount discount) {
        Invoice invoice = resolveInvoice(discount);
        if (invoice != null) assertQuoteNotShared(lockEditableInvoice(invoice.getId()));
    }

    private Invoice lockEditableInvoice(Long invoiceId) {
        Invoice invoice =
                invoiceRepository
                        .findForPayment(invoiceId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy hóa đơn: " + invoiceId));
        tenantGuard.assertBranchAssigned(
                invoice.getBranch().getId(),
                TenantGuard.ROLE_CASHIER,
                TenantGuard.ROLE_BRANCH_MANAGER,
                TenantGuard.ROLE_MANAGER);
        BigDecimal paid = paymentRepository.sumAmountByInvoiceId(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.PENDING
                || invoice.getTableSession() == null
                || invoice.getTableSession().getStatus() != TableSessionStatus.WAITING_FOR_PAY
                || (paid != null && paid.signum() > 0)) {
            throw new ConflictException(
                    "Chỉ có thể sửa chiết khấu khi hóa đơn đang chờ thanh toán và chưa nhận tiền.");
        }
        return invoice;
    }

    private void removeInvoiceDiscounts(Invoice invoice) {
        manualDiscountRepository.findByInvoiceId(invoice.getId()).stream()
                .filter(
                        discount ->
                                discount.getOrder() == null && discount.getOrderDetail() == null)
                .forEach(this::softDeleteAndAudit);
        manualDiscountRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public ManualDiscountResponse getManualDiscountById(Long id) {
        ManualDiscount manualDiscount = findById(id);
        return manualDiscountMapper.toResponse(manualDiscount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManualDiscountResponse> getByOrder(Long orderId) {
        return manualDiscountRepository.findByOrderId(orderId).stream()
                .map(manualDiscountMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManualDiscountResponse> getByInvoice(Long invoiceId) {
        return manualDiscountRepository.findByInvoiceId(invoiceId).stream()
                .map(manualDiscountMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteManualDiscount(Long id) {
        ManualDiscount manualDiscount = findById(id);
        tenantGuard.assertBranch(resolveBranchId(manualDiscount));
        assertInvoiceStillEditable(manualDiscount);
        assertQuoteNotSharedForLegacyDiscount(manualDiscount);
        softDeleteAndAudit(manualDiscount);
        recalculateInvoiceOf(manualDiscount);
    }

    private void softDeleteAndAudit(ManualDiscount manualDiscount) {
        manualDiscount.softDelete(null);
        manualDiscountRepository.save(manualDiscount);

        auditLogService.record(
                AuditEvent.builder()
                        .entityType("ManualDiscount")
                        .entityId(manualDiscount.getId())
                        .actionType(AuditAction.DELETE)
                        .branchId(resolveBranchId(manualDiscount))
                        .valueBefore(
                                "type="
                                        + manualDiscount.getType()
                                        + "; value="
                                        + manualDiscount.getValue())
                        .reason("Xóa chiết khấu thủ công")
                        .build());
    }

    /** Hóa đơn (trực tiếp hoặc qua phiên của order) mà chiết khấu này thuộc về; null nếu chưa có. */
    private Invoice resolveInvoice(ManualDiscount md) {
        if (md.getInvoice() != null) {
            return md.getInvoice();
        }
        if (md.getOrder() != null && md.getOrder().getTableSession() != null) {
            return invoiceRepository
                    .findByTableSessionId(md.getOrder().getTableSession().getId())
                    .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED)
                    .orElse(null);
        }
        return null;
    }

    private void assertInvoiceStillEditable(ManualDiscount md) {
        Invoice invoice = resolveInvoice(md);
        if (invoice != null
                && (invoice.getStatus() == InvoiceStatus.PAID
                        || invoice.getStatus() == InvoiceStatus.ISSUED)) {
            throw new ConflictException(
                    "Hóa đơn đã thanh toán hoặc đã phát hành điện tử, không thể thay đổi chiết khấu (BR-09)");
        }
    }

    private void recalculateInvoiceOf(ManualDiscount md) {
        Invoice invoice = resolveInvoice(md);
        if (invoice != null) {
            invoiceService.recalculatePendingInvoice(invoice.getId());
        }
    }

    /** Chi nhánh của chiết khấu suy ra từ hóa đơn, hoặc từ phiên bàn của order. */
    private Long resolveBranchId(ManualDiscount md) {
        if (md.getInvoice() != null && md.getInvoice().getBranch() != null) {
            return md.getInvoice().getBranch().getId();
        }
        if (md.getOrder() != null
                && md.getOrder().getTableSession() != null
                && md.getOrder().getTableSession().getBranch() != null) {
            return md.getOrder().getTableSession().getBranch().getId();
        }
        return null;
    }

    private ManualDiscount findById(Long id) {
        return manualDiscountRepository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Không tìm thấy giảm giá thủ công với ID: " + id));
    }
}
