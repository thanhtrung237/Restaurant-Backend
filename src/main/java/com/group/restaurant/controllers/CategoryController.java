package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.category.CategoryAddRequest;
import com.group.restaurant.dtos.request.category.CategoryUpdateRequest;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.category.CategoryResponse;
import com.group.restaurant.enums.CategoryStatus;
import com.group.restaurant.services.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping("")
    public ResponseEntity<ResponseSuccess<CategoryResponse>> createCategory(
            @Valid @RequestBody CategoryAddRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Tạo danh mục thành công",
                categoryService.createCategory(request)
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseSuccess<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryUpdateRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Cập nhật danh mục thành công",
                categoryService.updateCategory(id, request)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<CategoryResponse>> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh mục thành công",
                categoryService.getCategoryById(id)
        ));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ResponseSuccess<List<CategoryResponse>>> getCategoriesByRestaurant(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) CategoryStatus status) {
        List<CategoryResponse> categories;
        if (status != null) {
            categories = categoryService.getCategoriesByRestaurantAndStatus(restaurantId, status);
        } else {
            categories = categoryService.getCategoriesByRestaurant(restaurantId);
        }
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách danh mục thành công",
                categories
        ));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ResponseSuccess<Void>> changeStatus(
            @PathVariable Long id,
            @RequestParam CategoryStatus status) {
        categoryService.changeStatus(id, status);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Thay đổi trạng thái danh mục thành công",
                null
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseSuccess<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Xóa danh mục thành công",
                null
        ));
    }
}
