package com.group.restaurant.dtos.response.payment;

import com.group.restaurant.enums.PaymentMethod;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PaymentResponse {

    private Long id;
    private Long invoiceId;
    private PaymentMethod method;
    private BigDecimal amount;
    private LocalDateTime paidAt;
    private String transactionRef;
    private BigDecimal amountTendered;
    private BigDecimal changeAmount;
    private LocalDateTime createdAt;
}
