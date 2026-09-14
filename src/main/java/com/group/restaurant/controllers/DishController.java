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
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/dishes")
@RequiredArgsConstructor
public class DishController {

    private final DishService dishService;

    @PostMapping("")
    public ResponseEntity<ResponseSuccess<DishResponse>> createDish(
            @Valid @RequestBody DishAddRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Tạo món ăn thành công",
                dishService.createDish(request)
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseSuccess<DishResponse>> updateDish(
            @PathVariable Long id,
            @Valid @RequestBody DishUpdateRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Cập nhật món ăn thành công",
                dishService.updateDish(id, request)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<DishResponse>> getDishById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy món ăn thành công",
                dishService.getDishById(id)
        ));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ResponseSuccess<PageResponse<DishResponse>>> getDishesByRestaurant(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) DishStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách món ăn thành công",
                dishService.getDishesByRestaurant(restaurantId, status, pageable)
        ));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ResponseSuccess<List<DishResponse>>> getDishesByBranch(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách món riêng của chi nhánh thành công",
                dishService.getDishesByBranch(branchId)
        ));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ResponseSuccess<List<DishResponse>>> getDishesByCategory(
            @PathVariable Long categoryId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách món theo danh mục thành công",
                dishService.getDishesByCategory(categoryId)
        ));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ResponseSuccess<Void>> changeStatus(
            @PathVariable Long id,
            @RequestParam DishStatus status) {
        dishService.changeStatus(id, status);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Thay đổi trạng thái món ăn thành công",
                null
        ));
    }

    @GetMapping("/{id}/affected-branches")
    public ResponseEntity<ResponseSuccess<Integer>> countAffectedBranches(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Số chi nhánh bị ảnh hưởng khi ngừng bán món",
                dishService.countBranchesAffectedByDeletion(id)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseSuccess<Void>> deleteDish(@PathVariable Long id) {
        dishService.deleteDish(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Xóa món ăn thành công",
                null
        ));
    }
}
