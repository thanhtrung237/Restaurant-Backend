package com.group.restaurant.dtos.request.payment;

import com.group.restaurant.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentAddRequest {

    @NotNull(message = "Invoice ID không được để trống")
    private Long invoiceId;

    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod method;

    @NotNull(message = "Số tiền thanh toán không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền thanh toán phải > 0")
    private BigDecimal amount;

    @Size(max = 200, message = "Mã giao dịch không được vượt quá 200 ký tự")
    private String transactionRef;

    private BigDecimal amountTendered;
}
