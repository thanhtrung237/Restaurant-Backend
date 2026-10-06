package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.role.RoleAddRequest;
import com.group.restaurant.dtos.request.role.RoleUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.role.RoleResponse;
import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.services.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    // Bảng 4.3 — "Cấu hình vai trò & quyền": chỉ Quản trị nhà hàng.
    // Trước đây toàn bộ controller này KHÔNG có chốt quyền nào.
    @PostMapping("")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<RoleResponse>> createRole(
            @Valid @RequestBody RoleAddRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Tạo vai trò thành công",
                roleService.createRole(request)
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<RoleResponse>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Cập nhật vai trò thành công",
                roleService.updateRole(id, request)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<RoleResponse>> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy vai trò thành công",
                roleService.getRoleById(id)
        ));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<RoleResponse>> getRoleByCode(@PathVariable String code) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy vai trò thành công",
                roleService.getRoleByCode(code)
        ));
    }

    @GetMapping("")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<PageResponse<RoleResponse>>> getAllRoles(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) RoleScope scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách vai trò thành công",
                roleService.getAllRoles(restaurantId, scope, pageable)
        ));
    }

    @GetMapping("/restaurant/{restaurantId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<List<RoleResponse>>> getRolesByRestaurant(
            @PathVariable Long restaurantId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách vai trò theo nhà hàng thành công",
                roleService.getRolesByRestaurant(restaurantId)
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<Void>> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Xóa vai trò thành công",
                null
        ));
    }
}
