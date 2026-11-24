package com.group.restaurant.dtos.response.auditLog;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AuditLogResponse {

    private Long id;

    private Long restaurantId;
    private String restaurantName;

    private Long branchId;
    private String branchName;

    private Long performedById;
    private String performedByName;

    private Long approvedById;
    private String approvedByName;

    private LocalDateTime occurredAt;

    private String entityType;
    private Long entityId;

    /** FR-LOG-01: CREATE / UPDATE / DELETE / STATUS_CHANGE / CANCEL / PAYMENT / DISCOUNT ... */
    private String actionType;

    private String valueBefore;
    private String valueAfter;
    private String reason;
}
