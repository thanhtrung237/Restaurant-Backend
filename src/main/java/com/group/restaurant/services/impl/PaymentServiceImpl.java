package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.payment.PaymentAddRequest;
import com.group.restaurant.dtos.response.payment.PaymentResponse;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.enums.PaymentMethod;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.PaymentMapper;
import com.group.restaurant.models.Invoice;
import com.group.restaurant.models.Payment;
import com.group.restaurant.repositories.InvoiceRepository;
import com.group.restaurant.repositories.PaymentRepository;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.OrderNotificationService;
import com.group.restaurant.services.PaymentService;
import com.group.restaurant.services.SepayTransfer;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.utils.TenantGuard;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentMapper paymentMapper;
    private final AuditLogService auditLogService;
    private final TenantGuard tenantGuard;
    private final OrderNotificationService orderNotificationService;
    private final SepayTransfer sepay;

    @Override
    public PaymentResponse processPayment(PaymentAddRequest request) {
        Invoice invoice =
                invoiceRepository
                        .findForPayment(request.getInvoiceId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy hóa đơn với ID: "
                                                        + request.getInvoiceId()));
        // invoiceId đến từ client: Thu ngân chỉ được thu tiền hóa đơn của nhà hàng mình.
        tenantGuard.assertBranchAssigned(invoice.getBranch().getId());
        boolean takeaway =
                invoice.getTableSession() != null && invoice.getTableSession().isTakeaway();
        if (takeaway) {
            tenantGuard.assertBranchAssigned(
                    invoice.getBranch().getId(),
                    TenantGuard.ROLE_CASHIER,
                    TenantGuard.ROLE_BRANCH_MANAGER,
                    TenantGuard.ROLE_MANAGER);
        }

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("Hóa đơn này đã được thanh toán đầy đủ trước đó");
        }
        if (invoice.getStatus() == InvoiceStatus.ISSUED) {
            throw new ConflictException(
                    "Hóa đơn này đã được thanh toán và phát hành điện tử, không thể thanh toán thêm");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new ConflictException("Hóa đơn này đã bị hủy, không thể thanh toán");
        }

        BigDecimal remaining =
                invoice.getGrandTotal()
                        .subtract(paymentRepository.sumAmountByInvoiceId(invoice.getId()));
        if (request.getAmount().compareTo(remaining) > 0) {
            throw new ConflictException(
                    "Số tiền thu vượt số tiền còn phải trả. Vui lòng kiểm tra lại hóa đơn.");
        }
        Payment payment = paymentMapper.toEntity(request);
        return buildPaymentResponse(savePayment(invoice, payment));
    }

    @Override
    public boolean recordVerifiedSepayTransfer(
            Long invoiceId, Long transactionId, BigDecimal amount) {
        if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) return false;

        Invoice invoice = invoiceRepository.findForPayment(invoiceId).orElse(null);
        if (invoice == null
                || invoice.getStatus() != InvoiceStatus.PENDING
                || !sepay.enabledFor(invoice.getBranch())
                || invoice.getTableSession() == null
                || invoice.getTableSession().getStatus() != TableSessionStatus.WAITING_FOR_PAY
                || paymentRepository.existsBySepayTransactionId(transactionId)) return false;

        BigDecimal remaining =
                invoice.getGrandTotal().subtract(paymentRepository.sumAmountByInvoiceId(invoiceId));
        if (amount.compareTo(remaining) != 0) return false;

        savePayment(
                invoice,
                Payment.builder()
                        .method(PaymentMethod.BANK_TRANSFER)
                        .amount(amount)
                        .transactionRef("SePay #" + transactionId)
                        .sepayTransactionId(transactionId)
                        .build());
        orderNotificationService.notifyBankPaymentVerified(invoice.getTableSession());
        return true;
    }

    private Payment savePayment(Invoice invoice, Payment payment) {
        payment.setInvoice(invoice);
        payment.setPaidAt(LocalDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);

        // Kiểm tra đã thu đủ tiền chưa
        BigDecimal totalPaid = paymentRepository.sumAmountByInvoiceId(invoice.getId());
        boolean fullyPaid = totalPaid != null && totalPaid.compareTo(invoice.getGrandTotal()) >= 0;
        if (fullyPaid) {
            // Chuyển invoice → PAID
            invoice.setStatus(InvoiceStatus.PAID);
            invoiceRepository.save(invoice);

            // KHÔNG tự động CLOSE session ở đây.
            // Session chỉ được đóng sau khi Thu ngân phát hành HĐĐT thành công
            // qua endpoint POST /invoices/{id}/issue → InvoiceService.issueEInvoice()
            // Điều này đúng với đặc tả: "Phát hành thất bại → CHẶN đóng bàn"
        }

        // FR-LOG-01: mọi lần thu tiền đều phải có vết để đối soát ca/két.
        auditLogService.record(
                AuditEvent.builder()
                        .entityType("Payment")
                        .entityId(savedPayment.getId())
                        .actionType(AuditAction.PAYMENT)
                        .branchId(invoice.getBranch() != null ? invoice.getBranch().getId() : null)
                        .valueAfter(
                                "invoice="
                                        + invoice.getInvoiceNumber()
                                        + "; method="
                                        + savedPayment.getMethod()
                                        + "; amount="
                                        + savedPayment.getAmount()
                                        + "; totalPaid="
                                        + totalPaid
                                        + "; grandTotal="
                                        + invoice.getGrandTotal()
                                        + "; invoiceStatus="
                                        + invoice.getStatus())
                        .reason(fullyPaid ? "Thu đủ tiền hóa đơn" : "Thu một phần hóa đơn")
                        .build());

        orderNotificationService.notifySessionUpdated(invoice.getTableSession());
        return savedPayment;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByInvoice(Long invoiceId) {
        Invoice invoice =
                invoiceRepository
                        .findById(invoiceId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy hóa đơn với ID: " + invoiceId));
        tenantGuard.assertBranch(invoice.getBranch());

        List<Payment> payments = paymentRepository.findByInvoiceId(invoiceId);
        return payments.stream().map(this::buildPaymentResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        Payment payment =
                paymentRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy thanh toán với ID: " + id));
        return buildPaymentResponse(payment);
    }

    private PaymentResponse buildPaymentResponse(Payment payment) {
        PaymentResponse response = paymentMapper.toResponse(payment);
        if (payment.getAmountTendered() != null && payment.getAmount() != null) {
            BigDecimal changeAmount = payment.getAmountTendered().subtract(payment.getAmount());
            if (changeAmount.compareTo(BigDecimal.ZERO) < 0) {
                changeAmount = BigDecimal.ZERO;
            }
            response.setChangeAmount(changeAmount);
        } else {
            response.setChangeAmount(BigDecimal.ZERO);
        }
        return response;
    }
}
