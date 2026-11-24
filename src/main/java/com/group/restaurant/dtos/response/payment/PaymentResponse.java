package com.group.restaurant.dtos.response.payment;

import com.group.restaurant.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

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
    private boolean bankVerified;
    private BigDecimal amountTendered;
    private BigDecimal changeAmount;
    private LocalDateTime createdAt;
}
