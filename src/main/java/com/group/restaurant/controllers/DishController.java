package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.dish.DishAddRequest;
import com.group.restaurant.dtos.request.dish.DishUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.dish.DishResponse;
import com.group.restaurant.enums.DishStatus;
import com.group.restaurant.services.DishService;
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
@RequestMapping("${api.prefix}/dishes")
@RequiredArgsConstructor
public class DishController {

    private final DishService dishService;

    // Quản trị nhà hàng quản lý món gốc (FR-MNU-02)
    // Quản lý chi nhánh tạo món riêng cho chi nhánh (FR-MNU-04)
    @PostMapping("")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<DishResponse>> createDish(
            @Valid @RequestBody DishAddRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED, "Tạo món ăn thành công", dishService.createDish(request)
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<DishResponse>> updateDish(
            @PathVariable Long id, @Valid @RequestBody DishUpdateRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK, "Cập nhật món ăn thành công", dishService.updateDish(id, request)
        ));
    }

    // Khách hàng (không đăng nhập, vào qua QR) và nhân viên đều được xem chi tiết món
    @GetMapping("/{id}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ResponseSuccess<DishResponse>> getDishById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK, "Lấy món ăn thành công", dishService.getDishById(id)
        ));
    }

    @GetMapping("/restaurant/{restaurantId}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ResponseSuccess<PageResponse<DishResponse>>> getDishesByRestaurant(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) DishStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK, "Lấy danh sách món ăn thành công",
                dishService.getDishesByRestaurant(restaurantId, status, pageable)
        ));
    }

    @GetMapping("/branch/{branchId}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ResponseSuccess<List<DishResponse>>> getDishesByBranch(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK, "Lấy danh sách món riêng của chi nhánh thành công",
                dishService.getDishesByBranch(branchId)
        ));
    }

    @GetMapping("/category/{categoryId}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ResponseSuccess<List<DishResponse>>> getDishesByCategory(
            @PathVariable Long categoryId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK, "Lấy danh sách món theo danh mục thành công",
                dishService.getDishesByCategory(categoryId)
        ));
    }

    // Quản trị NH và QL chi nhánh đổi trạng thái (FR-MNU-03)
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<Void>> changeStatus(
            @PathVariable Long id, @RequestParam DishStatus status) {
        dishService.changeStatus(id, status);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Thay đổi trạng thái món ăn thành công", null));
    }

    // QĐ-05: số chi nhánh ĐANG BÁN món này — dùng cho cảnh báo trước khi ngừng bán / xoá.
    @GetMapping("/{id}/affected-branches")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<Integer>> countAffectedBranches(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK, "Số chi nhánh đang bán món", dishService.countBranchesSellingDish(id)
        ));
    }

    // Chỉ Quản trị nhà hàng xóa món gốc (FR-MNU-02)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<Void>> deleteDish(@PathVariable Long id) {
        dishService.deleteDish(id);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Xóa món ăn thành công", null));
    }
}