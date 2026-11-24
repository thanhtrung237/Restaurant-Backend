package com.group.restaurant.services;

import com.group.restaurant.dtos.response.auditLog.AuditLogResponse;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.services.audit.AuditEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface AuditLogService {

    /**
     * Ghi một dòng nhật ký (FR-LOG-01).
     *
     * Trước đây service này CHỈ có các hàm đọc — không nơi nào trong hệ thống ghi
     * `audit_log`, nên màn "Nhật ký hệ thống" luôn rỗng.
     *
     * Hợp đồng quan trọng: hàm này KHÔNG được làm fail nghiệp vụ chính. Lỗi khi ghi
     * nhật ký chỉ được log lại, không ném ra ngoài (xem phần impl).
     */
    void record(AuditEvent event);

    /** Ghi nhật ký cùng giao dịch với thay đổi phân quyền. */
    void recordInTransaction(AuditEvent event);

    AuditLogResponse getAuditLogById(Long id);

    PageResponse<AuditLogResponse> getAllAuditLogs(
            Long restaurantId,
            Long branchId,
            String entityType,
            /** be-issues 22 - loc theo loai thao tac; null / rong = khong loc. */
            List<String> actionTypes,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);

    List<AuditLogResponse> getAuditLogsByRestaurant(Long restaurantId);

    List<AuditLogResponse> getAuditLogsByBranch(Long branchId);

    List<AuditLogResponse> getAuditLogsByEntityType(String entityType);

    List<AuditLogResponse> getAuditLogsByPerformedBy(Long accountId);
}
