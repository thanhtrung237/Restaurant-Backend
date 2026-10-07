package com.group.restaurant.dtos.response.invoice;

import com.group.restaurant.enums.InvoiceStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class InvoiceListResponse {

    private Long id;
    private String invoiceNumber;
    private String branchName;
    private String tableName;
    private BigDecimal grandTotal;
    private InvoiceStatus status;
    private LocalDateTime createdAt;
}
