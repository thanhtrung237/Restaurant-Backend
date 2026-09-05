package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.assignment.AssignmentAddRequest;
import com.group.restaurant.dtos.response.assignment.AssignmentResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.services.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @PostMapping("")
    public ResponseEntity<ResponseSuccess<AssignmentResponse>> createAssignment(
            @Valid @RequestBody AssignmentAddRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Tạo phân quyền thành công",
                assignmentService.createAssignment(request)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<AssignmentResponse>> getAssignmentById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy phân quyền thành công",
                assignmentService.getAssignmentById(id)
        ));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<ResponseSuccess<List<AssignmentResponse>>> getAssignmentsByAccount(
            @PathVariable Long accountId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách phân quyền theo tài khoản thành công",
                assignmentService.getAssignmentsByAccount(accountId)
        ));
    }

    @GetMapping("/role/{roleId}")
    public ResponseEntity<ResponseSuccess<List<AssignmentResponse>>> getAssignmentsByRole(
            @PathVariable Long roleId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách phân quyền theo vai trò thành công",
                assignmentService.getAssignmentsByRole(roleId)
        ));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ResponseSuccess<List<AssignmentResponse>>> getAssignmentsByBranch(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách phân quyền theo chi nhánh thành công",
                assignmentService.getAssignmentsByBranch(branchId)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseSuccess<Void>> deleteAssignment(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Xóa phân quyền thành công",
                null
        ));
    }
}
