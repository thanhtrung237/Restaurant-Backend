package com.group.restaurant.controllers;

import com.group.restaurant.dtos.response.auditLog.AuditLogResponse;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    // FR-LOG-01 — "QL chi nhánh chỉ xem nhật ký chi nhánh mình (BR-18); Quản trị nhà
    // hàng xem toàn bộ nhà hàng." Phục vụ / Thu ngân KHÔNG có quyền xem nhật ký.
    // Giới hạn theo chi nhánh được phân công nằm ở service.
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<AuditLogResponse>> getAuditLogById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy nhật ký thành công",
                auditLogService.getAuditLogById(id)
        ));
    }

    @GetMapping("")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<PageResponse<AuditLogResponse>>> getAllAuditLogs(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String entityType,
            // be-issues 22 / FR-LOG-01: loc theo loai hanh dong, nhan mot hoac nhieu gia tri
            // (?actionType=LOGIN&actionType=LOGOUT). Bo trong = khong loc.
            @RequestParam(required = false) List<String> actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        // Nhật ký luôn đọc theo thứ tự MỚI NHẤT TRƯỚC. Thiếu sort thì thứ tự do DB quyết
        // định (thực tế ra cũ-nhất-trước), nên trang 1 của màn Nhật ký toàn bản ghi cũ
        // nhất — người dùng tưởng hệ thống không ghi gì thêm.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách nhật ký thành công",
                auditLogService.getAllAuditLogs(
                        restaurantId, branchId, entityType, actionType, startDate, endDate, pageable)
        ));
    }

    @GetMapping("/restaurant/{restaurantId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<List<AuditLogResponse>>> getAuditLogsByRestaurant(
            @PathVariable Long restaurantId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách nhật ký theo nhà hàng thành công",
                auditLogService.getAuditLogsByRestaurant(restaurantId)
        ));
    }

    @GetMapping("/branch/{branchId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<List<AuditLogResponse>>> getAuditLogsByBranch(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách nhật ký theo chi nhánh thành công",
                auditLogService.getAuditLogsByBranch(branchId)
        ));
    }

    @GetMapping("/entity-type/{entityType}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<List<AuditLogResponse>>> getAuditLogsByEntityType(
            @PathVariable String entityType) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách nhật ký theo loại đối tượng thành công",
                auditLogService.getAuditLogsByEntityType(entityType)
        ));
    }

    @GetMapping("/performed-by/{accountId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<List<AuditLogResponse>>> getAuditLogsByPerformedBy(
            @PathVariable Long accountId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách nhật ký theo người thực hiện thành công",
                auditLogService.getAuditLogsByPerformedBy(accountId)
        ));
    }
}
