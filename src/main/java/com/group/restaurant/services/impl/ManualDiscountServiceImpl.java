package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.manualDiscount.ManualDiscountAddRequest;
import com.group.restaurant.dtos.response.manualDiscount.ManualDiscountResponse;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.ManualDiscountMapper;
import com.group.restaurant.models.Invoice;
import com.group.restaurant.models.ManualDiscount;
import com.group.restaurant.models.Order;
import com.group.restaurant.repositories.InvoiceRepository;
import com.group.restaurant.repositories.ManualDiscountRepository;
import com.group.restaurant.repositories.OrderRepository;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.InvoiceService;
import com.group.restaurant.services.ManualDiscountService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.utils.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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

    @Override
    public ManualDiscountResponse createManualDiscount(ManualDiscountAddRequest request) {
        ManualDiscount manualDiscount = ManualDiscount.builder()
                .type(request.getType())
                .value(request.getValue())
                .reason(request.getReason())
                .build();

        if (request.getOrderId() != null) {
            Order order = orderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy đơn hàng với ID: " + request.getOrderId()));
            manualDiscount.setOrder(order);
        }

        if (request.getInvoiceId() != null) {
            Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy hóa đơn với ID: " + request.getInvoiceId()));
            manualDiscount.setInvoice(invoice);
        }

        // Chặn tạo chiết khấu lên order/hóa đơn của nhà hàng khác.
        tenantGuard.assertBranch(resolveBranchId(manualDiscount));

        // BR-09: hóa đơn đã thu đủ / đã phát hành là bất biến — không thêm chiết khấu vào nữa.
        assertInvoiceStillEditable(manualDiscount);

        ManualDiscount saved = manualDiscountRepository.save(manualDiscount);

        // FR-LOG-01: chiết khấu tay là hành động dễ bị lạm dụng nhất — luôn phải có vết,
        // kèm chi nhánh để QL chi nhánh đối soát được.
        auditLogService.record(AuditEvent.builder()
                .entityType("ManualDiscount")
                .entityId(saved.getId())
                .actionType(AuditAction.DISCOUNT)
                .branchId(resolveBranchId(saved))
                .valueAfter("type=" + saved.getType()
                        + "; value=" + saved.getValue()
                        + "; orderId=" + (saved.getOrder() != null ? saved.getOrder().getId() : null)
                        + "; invoiceId=" + (saved.getInvoice() != null ? saved.getInvoice().getId() : null))
                .reason(saved.getReason())
                .build());

        // Luồng Thu ngân là "lập hóa đơn trước, chiết khấu sau": phải tính lại hóa đơn PENDING,
        // nếu không invoice.totalManualDiscount / grandTotal (QR, Payment) vẫn là số cũ.
        recalculateInvoiceOf(saved);

        return manualDiscountMapper.toResponse(saved);
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
        return manualDiscountRepository.findByOrderId(orderId)
                .stream()
                .map(manualDiscountMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManualDiscountResponse> getByInvoice(Long invoiceId) {
        return manualDiscountRepository.findByInvoiceId(invoiceId)
                .stream()
                .map(manualDiscountMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteManualDiscount(Long id) {
        ManualDiscount manualDiscount = findById(id);
        tenantGuard.assertBranch(resolveBranchId(manualDiscount));
        assertInvoiceStillEditable(manualDiscount);
        manualDiscount.softDelete(null);
        manualDiscountRepository.save(manualDiscount);

        auditLogService.record(AuditEvent.builder()
                .entityType("ManualDiscount")
                .entityId(id)
                .actionType(AuditAction.DELETE)
                .branchId(resolveBranchId(manualDiscount))
                .valueBefore("type=" + manualDiscount.getType() + "; value=" + manualDiscount.getValue())
                .reason("Xóa chiết khấu thủ công")
                .build());

        // Xóa chiết khấu cũng phải cập nhật lại hóa đơn PENDING (nếu có).
        recalculateInvoiceOf(manualDiscount);
    }

    /** Hóa đơn (trực tiếp hoặc qua phiên của order) mà chiết khấu này thuộc về; null nếu chưa có. */
    private Invoice resolveInvoice(ManualDiscount md) {
        if (md.getInvoice() != null) {
            return md.getInvoice();
        }
        if (md.getOrder() != null && md.getOrder().getTableSession() != null) {
            return invoiceRepository.findByTableSessionId(md.getOrder().getTableSession().getId())
                    .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED)
                    .orElse(null);
        }
        return null;
    }

    private void assertInvoiceStillEditable(ManualDiscount md) {
        Invoice invoice = resolveInvoice(md);
        if (invoice != null
                && (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.ISSUED)) {
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
        return manualDiscountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giảm giá thủ công với ID: " + id));
    }
}
