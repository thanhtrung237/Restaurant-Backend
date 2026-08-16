package com.group.restaurant.dtos.response.auditLog;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


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
    private String valueBefore;
    private String valueAfter;
    private String reason;
}
