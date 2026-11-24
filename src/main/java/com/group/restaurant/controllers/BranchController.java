package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.request.branch.BranchAddRequest;
import com.group.restaurant.dtos.request.branch.BranchAdminConfigRequest;
import com.group.restaurant.dtos.request.branch.BranchCopyConfigRequest;
import com.group.restaurant.dtos.request.branch.BranchManagerConfigRequest;
import com.group.restaurant.dtos.request.branch.BranchUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.branch.BranchListResponse;
import com.group.restaurant.dtos.response.branch.BranchResponse;
import com.group.restaurant.enums.BranchStatus;
import com.group.restaurant.services.BranchService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    // Quản trị nhà hàng tạo chi nhánh (FR-BRN-01)
    @PostMapping("")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<BranchResponse>> createBranch(
            @Valid @RequestBody BranchAddRequest request) {
        return ResponseEntity.status(CREATED)
                .body(
                        new ResponseSuccess<>(
                                CREATED,
                                "Tạo chi nhánh thành công",
                                branchService.createBranch(request)));
    }

    // Quản trị nhà hàng cập nhật chi nhánh
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<BranchResponse>> updateBranch(
            @PathVariable Long id, @Valid @RequestBody BranchUpdateRequest request) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Cập nhật chi nhánh thành công",
                        branchService.updateBranch(id, request)));
    }

    // Tất cả nhân viên xem được chi nhánh mình
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseSuccess<BranchResponse>> getBranchById(@PathVariable Long id) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK, "Lấy chi nhánh thành công", branchService.getBranchById(id)));
    }

    @GetMapping("/restaurant/{restaurantId}/code/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseSuccess<BranchResponse>> getBranchByCode(
            @PathVariable Long restaurantId, @PathVariable String code) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy chi nhánh thành công",
                        branchService.getBranchByCode(restaurantId, code)));
    }

    @GetMapping("")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER', 'PLATFORM_ADMIN')")
    public ResponseEntity<ResponseSuccess<PageResponse<BranchListResponse>>> getAllBranches(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) BranchStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách chi nhánh thành công",
                        branchService.getAllBranches(restaurantId, status, pageable)));
    }

    @GetMapping("/restaurant/{restaurantId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER', 'PLATFORM_ADMIN')")
    public ResponseEntity<ResponseSuccess<List<BranchListResponse>>> getBranchesByRestaurant(
            @PathVariable Long restaurantId) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách chi nhánh theo nhà hàng thành công",
                        branchService.getBranchesByRestaurant(restaurantId)));
    }

    // Quản trị nhà hàng thay đổi trạng thái chi nhánh
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<Void>> changeStatus(
            @PathVariable Long id, @RequestParam BranchStatus status) {
        branchService.changeStatus(id, status);
        return ResponseEntity.ok(
                new ResponseSuccess<>(OK, "Thay đổi trạng thái chi nhánh thành công", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<Void>> deleteBranch(@PathVariable Long id) {
        branchService.deleteBranch(id);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Xóa chi nhánh thành công", null));
    }

    // FR-BRN-02: Giờ giấc và timeout dùng chung. Tài khoản ngân hàng dành cho Quản trị nhà hàng.
    @PutMapping("/{id}/config/manager")
    @PreAuthorize(
            "hasRole('MANAGER') or (hasRole('BRANCH_MANAGER') and !#request.bankAccountNoProvided and !#request.bankNameProvided)")
    public ResponseEntity<ResponseSuccess<BranchResponse>> updateManagerConfig(
            @PathVariable Long id, @Valid @RequestBody BranchManagerConfigRequest request) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Cập nhật cấu hình chi nhánh thành công",
                        branchService.updateManagerConfig(id, request)));
    }

    // FR-BRN-02 Nhóm 2: CHỈ Quản trị nhà hàng cấu hình VAT, phí dịch vụ, ngưỡng chiết khấu
    @PutMapping("/{id}/config/admin")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<BranchResponse>> updateAdminConfig(
            @PathVariable Long id, @Valid @RequestBody BranchAdminConfigRequest request) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Cập nhật cấu hình tài chính chi nhánh thành công",
                        branchService.updateAdminConfig(id, request)));
    }

    // FR-BRN-01: Sao chép cấu hình từ chi nhánh có sẵn — chỉ Quản trị nhà hàng
    @PostMapping("/{id}/copy-config")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<BranchResponse>> copyConfig(
            @PathVariable Long id, @Valid @RequestBody BranchCopyConfigRequest request) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Sao chép cấu hình chi nhánh thành công",
                        branchService.copyConfigFromBranch(id, request)));
    }
}
