package com.group.restaurant.models;

import com.group.restaurant.enums.DiscountType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDiscountApproval {
    @Column(name = "discount_request_id", length = 36)
    private String requestId;
    @Column(name = "discount_requester_id")
    private Long requestedBy;
    @Column(name = "discount_request_type")
    private DiscountType type;
    @Column(name = "discount_request_value", precision = 10, scale = 2)
    private BigDecimal value;
    @Column(name = "discount_request_reason", length = 500)
    private String reason;
    @Column(name = "discount_request_quote_total", precision = 14, scale = 2)
    private BigDecimal quotedTotal;
}
