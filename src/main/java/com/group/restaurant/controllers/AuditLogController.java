package com.group.restaurant.controllers;

import com.group.restaurant.dtos.response.auditLog.AuditLogResponse;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<AuditLogResponse>> getAuditLogById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy nhật ký thành công",
                auditLogService.getAuditLogById(id)
        ));
    }

    @GetMapping("")
    public ResponseEntity<ResponseSuccess<PageResponse<AuditLogResponse>>> getAllAuditLogs(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách nhật ký thành công",
                auditLogService.getAllAuditLogs(restaurantId, branchId, entityType, startDate, endDate, pageable)
        ));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ResponseSuccess<List<AuditLogResponse>>> getAuditLogsByRestaurant(
            @PathVariable Long restaurantId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách nhật ký theo nhà hàng thành công",
                auditLogService.getAuditLogsByRestaurant(restaurantId)
        ));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ResponseSuccess<List<AuditLogResponse>>> getAuditLogsByBranch(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách nhật ký theo chi nhánh thành công",
                auditLogService.getAuditLogsByBranch(branchId)
        ));
    }

    @GetMapping("/entity-type/{entityType}")
    public ResponseEntity<ResponseSuccess<List<AuditLogResponse>>> getAuditLogsByEntityType(
            @PathVariable String entityType) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách nhật ký theo loại đối tượng thành công",
                auditLogService.getAuditLogsByEntityType(entityType)
        ));
    }

    @GetMapping("/performed-by/{accountId}")
    public ResponseEntity<ResponseSuccess<List<AuditLogResponse>>> getAuditLogsByPerformedBy(
            @PathVariable Long accountId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách nhật ký theo người thực hiện thành công",
                auditLogService.getAuditLogsByPerformedBy(accountId)
        ));
    }
}
