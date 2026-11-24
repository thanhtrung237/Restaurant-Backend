package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.request.dishOverride.DishOverrideAddRequest;
import com.group.restaurant.dtos.request.dishOverride.DishOverrideUpdateRequest;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.dishOverride.DishOverrideResponse;
import com.group.restaurant.services.DishOverrideService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/dish-overrides")
@RequiredArgsConstructor
public class DishOverrideController {

    private final DishOverrideService dishOverrideService;

    // Quản lý chi nhánh và Quản trị nhà hàng đè giá/ẩn món (FR-MNU-04)
    @PostMapping("")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<DishOverrideResponse>> createDishOverride(
            @Valid @RequestBody DishOverrideAddRequest request) {
        return ResponseEntity.status(CREATED)
                .body(
                        new ResponseSuccess<>(
                                CREATED,
                                "Tạo tùy chỉnh món theo chi nhánh thành công",
                                dishOverrideService.createDishOverride(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<DishOverrideResponse>> updateDishOverride(
            @PathVariable Long id, @Valid @RequestBody DishOverrideUpdateRequest request) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Cập nhật tùy chỉnh món theo chi nhánh thành công",
                        dishOverrideService.updateDishOverride(id, request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<DishOverrideResponse>> getDishOverrideById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy tùy chỉnh món theo chi nhánh thành công",
                        dishOverrideService.getDishOverrideById(id)));
    }

    @GetMapping("/branch/{branchId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<List<DishOverrideResponse>>> getDishOverridesByBranch(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách tùy chỉnh món theo chi nhánh thành công",
                        dishOverrideService.getDishOverridesByBranch(branchId)));
    }

    @GetMapping("/dish/{dishId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<List<DishOverrideResponse>>> getDishOverridesByDish(
            @PathVariable Long dishId) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách tùy chỉnh món theo món ăn thành công",
                        dishOverrideService.getDishOverridesByDish(dishId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<Void>> deleteDishOverride(@PathVariable Long id) {
        dishOverrideService.deleteDishOverride(id);
        return ResponseEntity.ok(
                new ResponseSuccess<>(OK, "Xóa tùy chỉnh món theo chi nhánh thành công", null));
    }
}
