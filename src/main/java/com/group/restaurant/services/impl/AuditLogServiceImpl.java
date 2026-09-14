package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.response.auditLog.AuditLogResponse;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.AuditLogMapper;
import com.group.restaurant.models.AuditLog;
import com.group.restaurant.repositories.AuditLogRepository;
import com.group.restaurant.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    @Override
    public AuditLogResponse getAuditLogById(Long id) {
        AuditLog auditLog = findAuditLogById(id);
        return auditLogMapper.toResponse(auditLog);
    }

    @Override
    public PageResponse<AuditLogResponse> getAllAuditLogs(
            Long restaurantId,
            Long branchId,
            String entityType,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        Page<AuditLog> auditLogPage;

        if (restaurantId != null && startDate != null && endDate != null) {
            auditLogPage = auditLogRepository.findByRestaurantIdAndDateRange(
                    restaurantId, startDate, endDate, pageable);
        } else if (restaurantId != null) {
            auditLogPage = auditLogRepository.findByRestaurantId(restaurantId, pageable);
        } else if (branchId != null) {
            auditLogPage = auditLogRepository.findByBranchId(branchId, pageable);
        } else if (entityType != null) {
            auditLogPage = auditLogRepository.findByEntityType(entityType, pageable);
        } else {
            auditLogPage = auditLogRepository.findAll(pageable);
        }

        List<AuditLogResponse> responses = auditLogPage.getContent().stream()
                .map(auditLogMapper::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<AuditLogResponse>builder()
                .content(responses)
                .pageNumber(auditLogPage.getNumber())
                .pageSize(auditLogPage.getSize())
                .totalElements(auditLogPage.getTotalElements())
                .totalPages(auditLogPage.getTotalPages())
                .first(auditLogPage.isFirst())
                .last(auditLogPage.isLast())
                .build();
    }

    @Override
    public List<AuditLogResponse> getAuditLogsByRestaurant(Long restaurantId) {
        Page<AuditLog> auditLogsPage = auditLogRepository.findByRestaurantId(restaurantId, Pageable.unpaged());
        return auditLogsPage.getContent().stream()
                .map(auditLogMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditLogResponse> getAuditLogsByBranch(Long branchId) {
        Page<AuditLog> auditLogsPage = auditLogRepository.findByBranchId(branchId, Pageable.unpaged());
        return auditLogsPage.getContent().stream()
                .map(auditLogMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditLogResponse> getAuditLogsByEntityType(String entityType) {
        Page<AuditLog> auditLogsPage = auditLogRepository.findByEntityType(entityType, Pageable.unpaged());
        return auditLogsPage.getContent().stream()
                .map(auditLogMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditLogResponse> getAuditLogsByPerformedBy(Long accountId) {
        Page<AuditLog> auditLogsPage = auditLogRepository.findByPerformedById(accountId, Pageable.unpaged());
        return auditLogsPage.getContent().stream()
                .map(auditLogMapper::toResponse)
                .collect(Collectors.toList());
    }

    private AuditLog findAuditLogById(Long id) {
        return auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhật ký với ID: " + id));
    }
}
