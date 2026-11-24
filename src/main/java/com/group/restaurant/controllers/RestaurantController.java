package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.request.restaurant.RestaurantAddRequest;
import com.group.restaurant.dtos.request.restaurant.RestaurantUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.restaurant.RestaurantListResponse;
import com.group.restaurant.dtos.response.restaurant.RestaurantResponse;
import com.group.restaurant.enums.RestaurantStatus;
import com.group.restaurant.services.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    // Quản trị nền tảng tạo nhà hàng (FR-TEN-01)
    @PostMapping("")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ResponseSuccess<RestaurantResponse>> createRestaurant(
            @Valid @RequestBody RestaurantAddRequest request) {
        return ResponseEntity.status(CREATED)
                .body(
                        new ResponseSuccess<>(
                                CREATED,
                                "Tạo nhà hàng thành công",
                                restaurantService.createRestaurant(request)));
    }

    // Quản trị nhà hàng cập nhật thông tin (FR-ADM-03), Quản trị nền tảng cập nhật (FR-TEN-01)
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'PLATFORM_ADMIN')")
    public ResponseEntity<ResponseSuccess<RestaurantResponse>> updateRestaurant(
            @PathVariable Long id, @Valid @RequestBody RestaurantUpdateRequest request) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Cập nhật nhà hàng thành công",
                        restaurantService.updateRestaurant(id, request)));
    }

    // Tất cả nhân viên có thể xem nhà hàng mình thuộc về
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseSuccess<RestaurantResponse>> getRestaurantById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK, "Lấy nhà hàng thành công", restaurantService.getRestaurantById(id)));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseSuccess<RestaurantResponse>> getRestaurantByCode(
            @PathVariable String code) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy nhà hàng thành công",
                        restaurantService.getRestaurantByCode(code)));
    }

    // Chỉ nền tảng xem tất cả
    @GetMapping("")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ResponseSuccess<PageResponse<RestaurantListResponse>>> getAllRestaurants(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) RestaurantStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách nhà hàng thành công",
                        restaurantService.getAllRestaurants(keyword, status, pageable)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ResponseSuccess<Void>> changeStatus(
            @PathVariable Long id, @RequestParam RestaurantStatus status) {
        restaurantService.changeStatus(id, status);
        return ResponseEntity.ok(
                new ResponseSuccess<>(OK, "Thay đổi trạng thái nhà hàng thành công", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ResponseSuccess<Void>> deleteRestaurant(@PathVariable Long id) {
        restaurantService.deleteRestaurant(id);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Xóa nhà hàng thành công", null));
    }
}
