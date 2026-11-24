package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.request.assignment.AssignmentAddRequest;
import com.group.restaurant.dtos.response.assignment.AssignmentResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.services.AssignmentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    // Bảng 4.3 — "Quản lý nhân viên" / "Cấu hình vai trò & quyền": chỉ Quản trị nhà hàng.
    // Trước đây endpoint này KHÔNG có chốt quyền: bất kỳ tài khoản đã đăng nhập nào cũng
    // tự gán được vai trò MANAGER cho chính mình (leo quyền trong cùng nhà hàng).
    @PostMapping("")
    @PreAuthorize("hasAnyRole('MANAGER', 'PLATFORM_ADMIN')")
    public ResponseEntity<ResponseSuccess<AssignmentResponse>> createAssignment(
            @Valid @RequestBody AssignmentAddRequest request) {
        return ResponseEntity.status(CREATED)
                .body(
                        new ResponseSuccess<>(
                                CREATED,
                                "Tạo phân quyền thành công",
                                assignmentService.createAssignment(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<AssignmentResponse>> getAssignmentById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK, "Lấy phân quyền thành công", assignmentService.getAssignmentById(id)));
    }

    // NGOẠI LỆ CÓ CHỦ Ý: màn đăng nhập và thanh đổi chi nhánh của MỌI vai trò đều gọi
    // endpoint này cho CHÍNH MÌNH (LoginResponse không trả branchId). Khoá về MANAGER
    // là gãy đăng nhập của Phục vụ / Thu ngân / QL chi nhánh.
    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'PLATFORM_ADMIN') or @tenantGuard.isSelf(#accountId)")
    public ResponseEntity<ResponseSuccess<List<AssignmentResponse>>> getAssignmentsByAccount(
            @PathVariable Long accountId) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách phân quyền theo tài khoản thành công",
                        assignmentService.getAssignmentsByAccount(accountId)));
    }

    @GetMapping("/role/{roleId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<List<AssignmentResponse>>> getAssignmentsByRole(
            @PathVariable Long roleId) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách phân quyền theo vai trò thành công",
                        assignmentService.getAssignmentsByRole(roleId)));
    }

    // Danh sách nhân sự của một chi nhánh. Chốt BR-18 nằm ở service (chỉ QL chi nhánh của
    // đúng chi nhánh đó, hoặc Quản trị nhà hàng).
    @GetMapping("/branch/{branchId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<List<AssignmentResponse>>> getAssignmentsByBranch(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách phân quyền theo chi nhánh thành công",
                        assignmentService.getAssignmentsByBranch(branchId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<Void>> deleteAssignment(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Xóa phân quyền thành công", null));
    }
}
