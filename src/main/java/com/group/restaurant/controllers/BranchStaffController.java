package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.services.BranchStaffService;
import com.group.restaurant.services.BranchStaffService.CreateStaff;
import com.group.restaurant.services.BranchStaffService.StaffMember;
import com.group.restaurant.services.BranchStaffService.UpdateRoles;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/branches/{branchId}/staff")
@PreAuthorize("hasRole('BRANCH_MANAGER')")
@RequiredArgsConstructor
public class BranchStaffController {
    private final BranchStaffService staff;

    @GetMapping
    public ResponseSuccess<List<StaffMember>> list(@PathVariable Long branchId) {
        return new ResponseSuccess<>(OK, "Nhân viên chi nhánh", staff.list(branchId));
    }

    @PostMapping
    public ResponseEntity<ResponseSuccess<StaffMember>> create(
            @PathVariable Long branchId, @Valid @RequestBody CreateStaff request) {
        return ResponseEntity.status(CREATED)
                .body(
                        new ResponseSuccess<>(
                                CREATED,
                                "Đã tạo nhân viên chi nhánh",
                                staff.create(branchId, request)));
    }

    @PutMapping("/{accountId}/roles")
    public ResponseSuccess<StaffMember> update(
            @PathVariable Long branchId,
            @PathVariable Long accountId,
            @Valid @RequestBody UpdateRoles request) {
        return new ResponseSuccess<>(
                OK, "Đã lưu phân công", staff.update(branchId, accountId, request));
    }
}
