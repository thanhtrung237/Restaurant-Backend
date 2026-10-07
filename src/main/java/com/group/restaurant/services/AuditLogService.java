package com.group.restaurant.services;

import com.group.restaurant.dtos.response.auditLog.AuditLogResponse;
import com.group.restaurant.dtos.response.base.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogService {

    AuditLogResponse getAuditLogById(Long id);

    PageResponse<AuditLogResponse> getAllAuditLogs(
            Long restaurantId,
            Long branchId,
            String entityType,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    List<AuditLogResponse> getAuditLogsByRestaurant(Long restaurantId);

    List<AuditLogResponse> getAuditLogsByBranch(Long branchId);

    List<AuditLogResponse> getAuditLogsByEntityType(String entityType);

    List<AuditLogResponse> getAuditLogsByPerformedBy(Long accountId);
}
