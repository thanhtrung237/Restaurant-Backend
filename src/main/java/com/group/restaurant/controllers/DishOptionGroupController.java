package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.dishOptionGroup.DishOptionGroupAddRequest;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.dishOptionGroup.DishOptionGroupResponse;
import com.group.restaurant.services.DishOptionGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/dish-option-groups")
@RequiredArgsConstructor
public class DishOptionGroupController {

    private final DishOptionGroupService dishOptionGroupService;

    @PostMapping("")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<DishOptionGroupResponse>> linkDishToOptionGroup(
            @Valid @RequestBody DishOptionGroupAddRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Liên kết món với nhóm tùy chọn thành công",
                dishOptionGroupService.linkDishToOptionGroup(request)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<DishOptionGroupResponse>> getDishOptionGroupById(
            @PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy liên kết món-nhóm tùy chọn thành công",
                dishOptionGroupService.getDishOptionGroupById(id)
        ));
    }

    @GetMapping("/dish/{dishId}")
    public ResponseEntity<ResponseSuccess<List<DishOptionGroupResponse>>> getDishOptionGroupsByDish(
            @PathVariable Long dishId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách nhóm tùy chọn của món ăn thành công",
                dishOptionGroupService.getDishOptionGroupsByDish(dishId)
        ));
    }

    @GetMapping("/option-group/{optionGroupId}")
    public ResponseEntity<ResponseSuccess<List<DishOptionGroupResponse>>> getDishOptionGroupsByOptionGroup(
            @PathVariable Long optionGroupId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách món ăn sử dụng nhóm tùy chọn thành công",
                dishOptionGroupService.getDishOptionGroupsByOptionGroup(optionGroupId)
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<Void>> unlinkDishFromOptionGroup(@PathVariable Long id) {
        dishOptionGroupService.unlinkDishFromOptionGroup(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Hủy liên kết món-nhóm tùy chọn thành công",
                null
        ));
    }
}
