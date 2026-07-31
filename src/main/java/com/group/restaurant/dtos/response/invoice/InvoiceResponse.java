package com.group.restaurant.dtos.response.invoice;

import com.group.restaurant.dtos.response.payment.PaymentResponse;
import com.group.restaurant.enums.InvoiceStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class InvoiceResponse {

    private Long id;
    private Long branchId;
    private String branchName;
    private Long tableSessionId;
    private String tableName;
    private Long promotionId;
    private String promotionName;
    private String invoiceNumber;
    private String invoiceNote;
    private BigDecimal subTotalBeforeDiscount;
    private BigDecimal totalManualDiscount;
    private BigDecimal serviceFee;
    private BigDecimal vatAmount;
    private BigDecimal grandTotal;
    private InvoiceStatus status;
    private List<PaymentResponse> payments;
    private BigDecimal totalPaid;
    private BigDecimal remainingAmount;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
