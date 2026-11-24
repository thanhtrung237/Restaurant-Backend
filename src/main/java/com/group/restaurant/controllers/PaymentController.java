package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.request.payment.PaymentAddRequest;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.payment.PaymentResponse;
import com.group.restaurant.services.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("")
    public ResponseEntity<ResponseSuccess<PaymentResponse>> processPayment(
            @Valid @RequestBody PaymentAddRequest request) {
        return ResponseEntity.status(CREATED)
                .body(
                        new ResponseSuccess<>(
                                CREATED,
                                "Xử lý thanh toán thành công",
                                paymentService.processPayment(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<PaymentResponse>> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy thông tin thanh toán thành công",
                        paymentService.getPaymentById(id)));
    }

    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<ResponseSuccess<List<PaymentResponse>>> getPaymentsByInvoice(
            @PathVariable Long invoiceId) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách thanh toán theo hóa đơn thành công",
                        paymentService.getPaymentsByInvoice(invoiceId)));
    }
}
