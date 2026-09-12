package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.promotion.PromotionAddRequest;
import com.group.restaurant.dtos.request.promotion.PromotionUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.promotion.PromotionResponse;
import com.group.restaurant.enums.PromotionStatus;
import com.group.restaurant.services.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping("")
    public ResponseEntity<ResponseSuccess<PromotionResponse>> createPromotion(
            @Valid @RequestBody PromotionAddRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Tạo khuyến mãi thành công",
                promotionService.createPromotion(request)
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseSuccess<PromotionResponse>> updatePromotion(
            @PathVariable Long id,
            @Valid @RequestBody PromotionUpdateRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Cập nhật khuyến mãi thành công",
                promotionService.updatePromotion(id, request)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<PromotionResponse>> getPromotionById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy khuyến mãi thành công",
                promotionService.getPromotionById(id)
        ));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ResponseSuccess<PageResponse<PromotionResponse>>> getPromotionsByRestaurant(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) PromotionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách khuyến mãi thành công",
                promotionService.getPromotionsByRestaurant(restaurantId, status, pageable)
        ));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ResponseSuccess<Void>> changeStatus(
            @PathVariable Long id,
            @RequestParam PromotionStatus status) {
        promotionService.changeStatus(id, status);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Thay đổi trạng thái khuyến mãi thành công",
                null
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseSuccess<Void>> deletePromotion(@PathVariable Long id) {
        promotionService.deletePromotion(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Xóa khuyến mãi thành công",
                null
        ));
    }
}
