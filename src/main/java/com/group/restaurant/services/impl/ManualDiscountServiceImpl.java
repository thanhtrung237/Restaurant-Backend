package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.manualDiscount.ManualDiscountAddRequest;
import com.group.restaurant.dtos.response.manualDiscount.ManualDiscountResponse;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.ManualDiscountMapper;
import com.group.restaurant.models.Invoice;
import com.group.restaurant.models.ManualDiscount;
import com.group.restaurant.models.Order;
import com.group.restaurant.repositories.InvoiceRepository;
import com.group.restaurant.repositories.ManualDiscountRepository;
import com.group.restaurant.repositories.OrderRepository;
import com.group.restaurant.services.ManualDiscountService;
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

        ManualDiscount saved = manualDiscountRepository.save(manualDiscount);
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
        manualDiscount.softDelete(null);
        manualDiscountRepository.save(manualDiscount);
    }

    private ManualDiscount findById(Long id) {
        return manualDiscountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giảm giá thủ công với ID: " + id));
    }
}
