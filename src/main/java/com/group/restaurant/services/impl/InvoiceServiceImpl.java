package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.invoice.InvoiceCreateRequest;
import com.group.restaurant.dtos.response.invoice.InvoiceListResponse;
import com.group.restaurant.dtos.response.invoice.InvoiceResponse;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.InvoiceMapper;
import com.group.restaurant.models.*;
import com.group.restaurant.repositories.*;
import com.group.restaurant.services.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;
    private final TableSessionRepository tableSessionRepository;
    private final PromotionRepository promotionRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceMapper invoiceMapper;

    @Override
    public InvoiceResponse createInvoice(InvoiceCreateRequest request) {
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + request.getBranchId()));

        TableSession session = tableSessionRepository.findById(request.getTableSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên làm việc với ID: " + request.getTableSessionId()));

        invoiceRepository.findByTableSessionId(request.getTableSessionId())
                .ifPresent(existingInv -> {
                    if (existingInv.getStatus() != InvoiceStatus.CANCELLED) {
                        throw new ConflictException("Hóa đơn cho phiên làm việc này đã tồn tại với mã: " + existingInv.getInvoiceNumber());
                    }
                });

        Promotion promotion = null;
        if (request.getPromotionId() != null) {
            promotion = promotionRepository.findById(request.getPromotionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khuyến mãi với ID: " + request.getPromotionId()));
        }

        List<Order> orders = orderRepository.findByTableSessionId(session.getId());
        Order primaryOrder = orders.isEmpty() ? null : orders.get(orders.size() - 1);

        List<OrderDetail> details = orderDetailRepository.findByTableSessionId(session.getId());

        if (orders.isEmpty() && details.isEmpty()) {
            throw new com.group.restaurant.exceptions.InvalidParamException(
                    "Phiên làm việc ID " + session.getId() + " chưa có đơn hàng hoặc món ăn nào để lập hóa đơn");
        }

        BigDecimal subTotal = details.stream()
                .map(d -> {
                    if (d.getPrice() != null) return d.getPrice();
                    if (d.getPriceAtOrderTime() != null && d.getQuantity() != null) {
                        return d.getPriceAtOrderTime().multiply(new BigDecimal(d.getQuantity()));
                    }
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal manualDiscount = BigDecimal.ZERO;
        BigDecimal serviceFee = BigDecimal.ZERO;
        BigDecimal vatAmount = BigDecimal.ZERO;

        BigDecimal grandTotal = subTotal.subtract(manualDiscount).add(serviceFee).add(vatAmount);
        if (grandTotal.compareTo(BigDecimal.ZERO) < 0) {
            grandTotal = BigDecimal.ZERO;
        }

        String invoiceNumber = "INV" + System.currentTimeMillis();

        Invoice invoice = Invoice.builder()
                .branch(branch)
                .tableSession(session)
                .promotion(promotion)
                .order(primaryOrder)
                .invoiceNumber(invoiceNumber)
                .invoiceNote(request.getInvoiceNote())
                .subTotalBeforeDiscount(subTotal)
                .totalManualDiscount(manualDiscount)
                .serviceFee(serviceFee)
                .vatAmount(vatAmount)
                .grandTotal(grandTotal)
                .status(InvoiceStatus.PENDING)
                .build();

        Invoice savedInvoice = invoiceRepository.save(invoice);
        return buildInvoiceResponse(savedInvoice);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Long id) {
        Invoice invoice = findInvoiceById(id);
        return buildInvoiceResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByTableSession(Long tableSessionId) {
        Invoice invoice = invoiceRepository.findByTableSessionId(tableSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn cho phiên làm việc ID: " + tableSessionId));
        return buildInvoiceResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceListResponse> getInvoicesByBranch(Long branchId, InvoiceStatus status) {
        if (!branchRepository.existsById(branchId)) {
            throw new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + branchId);
        }

        List<Invoice> invoices = (status != null)
                ? invoiceRepository.findByBranchIdAndStatus(branchId, status)
                : invoiceRepository.findByBranchId(branchId);

        return invoices.stream()
                .map(invoiceMapper::toListResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public void cancelInvoice(Long id) {
        Invoice invoice = findInvoiceById(id);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("Không thể hủy hóa đơn đã thanh toán");
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoiceRepository.save(invoice);
    }

    @Override
    public void deleteInvoice(Long id) {
        Invoice invoice = findInvoiceById(id);
        invoice.softDelete(null);
        invoiceRepository.save(invoice);
    }

    private Invoice findInvoiceById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn với ID: " + id));
    }

    private InvoiceResponse buildInvoiceResponse(Invoice invoice) {
        InvoiceResponse response = invoiceMapper.toResponse(invoice);

        BigDecimal totalPaid = paymentRepository.sumAmountByInvoiceId(invoice.getId());
        if (totalPaid == null) {
            totalPaid = BigDecimal.ZERO;
        }

        BigDecimal remainingAmount = invoice.getGrandTotal().subtract(totalPaid);
        if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) {
            remainingAmount = BigDecimal.ZERO;
        }

        response.setTotalPaid(totalPaid);
        response.setRemainingAmount(remainingAmount);
        return response;
    }
}
