package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.promotionBranch.PromotionBranchAddRequest;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.promotionBranch.PromotionBranchResponse;
import com.group.restaurant.services.PromotionBranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/promotion-branches")
@RequiredArgsConstructor
public class PromotionBranchController {

    private final PromotionBranchService promotionBranchService;

    @PostMapping("")
    public ResponseEntity<ResponseSuccess<PromotionBranchResponse>> assignPromotionToBranch(
            @Valid @RequestBody PromotionBranchAddRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Gán khuyến mãi cho chi nhánh thành công",
                promotionBranchService.assignPromotionToBranch(request)
        ));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ResponseSuccess<List<PromotionBranchResponse>>> getByBranch(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách khuyến mãi theo chi nhánh thành công",
                promotionBranchService.getByBranch(branchId)
        ));
    }

    @GetMapping("/promotion/{promotionId}")
    public ResponseEntity<ResponseSuccess<List<PromotionBranchResponse>>> getByPromotion(
            @PathVariable Long promotionId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách chi nhánh theo khuyến mãi thành công",
                promotionBranchService.getByPromotion(promotionId)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseSuccess<Void>> removePromotionFromBranch(@PathVariable Long id) {
        promotionBranchService.removePromotionFromBranch(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Gỡ khuyến mãi khỏi chi nhánh thành công",
                null
        ));
    }
}
