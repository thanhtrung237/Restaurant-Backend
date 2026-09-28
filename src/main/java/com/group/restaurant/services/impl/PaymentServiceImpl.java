package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.payment.PaymentAddRequest;
import com.group.restaurant.dtos.response.payment.PaymentResponse;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.PaymentMapper;
import com.group.restaurant.models.Invoice;
import com.group.restaurant.models.Payment;
import com.group.restaurant.repositories.InvoiceRepository;
import com.group.restaurant.repositories.PaymentRepository;
import com.group.restaurant.services.PaymentService;
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
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public PaymentResponse processPayment(PaymentAddRequest request) {
        Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hóa đơn với ID: " + request.getInvoiceId()));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("Hóa đơn này đã được thanh toán đầy đủ trước đó");
        }
        if (invoice.getStatus() == InvoiceStatus.ISSUED) {
            throw new ConflictException("Hóa đơn này đã được thanh toán và phát hành điện tử, không thể thanh toán thêm");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new ConflictException("Hóa đơn này đã bị hủy, không thể thanh toán");
        }

        Payment payment = paymentMapper.toEntity(request);
        payment.setInvoice(invoice);
        payment.setPaidAt(LocalDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);

        // Kiểm tra đã thu đủ tiền chưa
        BigDecimal totalPaid = paymentRepository.sumAmountByInvoiceId(invoice.getId());
        if (totalPaid != null && totalPaid.compareTo(invoice.getGrandTotal()) >= 0) {
            // Chuyển invoice → PAID
            invoice.setStatus(InvoiceStatus.PAID);
            invoiceRepository.save(invoice);

            // KHÔNG tự động CLOSE session ở đây.
            // Session chỉ được đóng sau khi Thu ngân phát hành HĐĐT thành công
            // qua endpoint POST /invoices/{id}/issue → InvoiceService.issueEInvoice()
            // Điều này đúng với đặc tả: "Phát hành thất bại → CHẶN đóng bàn"
        }

        return buildPaymentResponse(savedPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByInvoice(Long invoiceId) {
        if (!invoiceRepository.existsById(invoiceId)) {
            throw new ResourceNotFoundException("Không tìm thấy hóa đơn với ID: " + invoiceId);
        }

        List<Payment> payments = paymentRepository.findByInvoiceId(invoiceId);
        return payments.stream()
                .map(this::buildPaymentResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thanh toán với ID: " + id));
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
