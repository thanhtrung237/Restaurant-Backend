package com.group.restaurant.services;

import com.group.restaurant.dtos.request.payment.PaymentAddRequest;
import com.group.restaurant.dtos.response.payment.PaymentResponse;

import java.util.List;
import java.math.BigDecimal;

public interface PaymentService {

    PaymentResponse processPayment(PaymentAddRequest request);

    boolean recordVerifiedSepayTransfer(Long invoiceId, Long transactionId, BigDecimal amount);

    List<PaymentResponse> getPaymentsByInvoice(Long invoiceId);

    PaymentResponse getPaymentById(Long id);

  
}
