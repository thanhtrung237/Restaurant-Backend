package com.group.restaurant.dtos.response.manualDiscount;

import com.group.restaurant.enums.DiscountType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ManualDiscountResponse {

    private Long id;
    private Long approvedById;
    private String approvedByName;
    private Long orderId;
    private Long invoiceId;
    private Long createdById;
    private String createdByName;
    private DiscountType type;
    private BigDecimal value;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
