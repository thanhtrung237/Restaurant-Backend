package com.group.restaurant.services;

import com.group.restaurant.dtos.request.payment.PaymentAddRequest;
import com.group.restaurant.dtos.response.payment.PaymentResponse;

import java.util.List;

public interface PaymentService {

    PaymentResponse processPayment(PaymentAddRequest request);

    List<PaymentResponse> getPaymentsByInvoice(Long invoiceId);

    PaymentResponse getPaymentById(Long id);

    void deletePayment(Long id);
}
