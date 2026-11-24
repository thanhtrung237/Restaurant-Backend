package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.enums.OperationalPermission;
import com.group.restaurant.services.OperationalPermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/operational-permissions")
@RequiredArgsConstructor
public class OperationalPermissionController {
    private final OperationalPermissionService permissions;

    public record UpdatePermissions(
            @NotNull Set<OperationalPermission> expected,
            @NotNull Set<OperationalPermission> enabled) {}

    public record UpdateRoles(
            @NotNull @Size(min = 1, max = 3)
                    List<OperationalPermissionService.RoleUpdate> changes) {}

    @GetMapping("/roles")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseSuccess<List<OperationalPermissionService.RoleConfiguration>> roles() {
        return new ResponseSuccess<>(OK, "Quyền vận hành", permissions.configuration());
    }

    @PutMapping("/roles")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseSuccess<List<OperationalPermissionService.RoleConfiguration>> updateRoles(
            @Valid @RequestBody UpdateRoles request) {
        return new ResponseSuccess<>(
                OK, "Đã lưu quyền vận hành", permissions.updateRoles(request.changes()));
    }

    @PutMapping("/roles/{role}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseSuccess<OperationalPermissionService.RoleConfiguration> update(
            @PathVariable String role, @Valid @RequestBody UpdatePermissions request) {
        return new ResponseSuccess<>(
                OK,
                "Đã lưu quyền vận hành",
                permissions.update(role, request.expected(), request.enabled()));
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseSuccess<List<OperationalPermissionService.BranchPermissions>> employee(
            @PathVariable Long employeeId) {
        return new ResponseSuccess<>(
                OK, "Quyền theo chi nhánh", permissions.employeePermissions(employeeId));
    }

    @GetMapping("/branches/{branchId}/me")
    public ResponseSuccess<Set<OperationalPermission>> current(@PathVariable Long branchId) {
        return new ResponseSuccess<>(
                OK, "Quyền tại chi nhánh", permissions.currentPermissions(branchId));
    }
}
