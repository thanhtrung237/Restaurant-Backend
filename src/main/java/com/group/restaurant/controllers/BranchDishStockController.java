package com.group.restaurant.controllers;

import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.branchDishStock.BranchDishStockResponse;
import com.group.restaurant.services.BranchDishStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/branch-dish-stocks")
@RequiredArgsConstructor
public class BranchDishStockController {

    private final BranchDishStockService branchDishStockService;

    @PutMapping("/branch/{branchId}/dish/{dishId}/toggle")
    public ResponseEntity<ResponseSuccess<BranchDishStockResponse>> toggleStock(
            @PathVariable Long branchId,
            @PathVariable Long dishId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Cập nhật trạng thái tồn kho thành công",
                branchDishStockService.toggleStock(branchId, dishId)
        ));
    }

    @GetMapping("/branch/{branchId}/out-of-stock")
    public ResponseEntity<ResponseSuccess<List<BranchDishStockResponse>>> getOutOfStock(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách món hết hàng thành công",
                branchDishStockService.getOutOfStockByBranch(branchId)
        ));
    }


    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ResponseSuccess<List<BranchDishStockResponse>>> getAllStock(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách tồn kho thành công",
                branchDishStockService.getAllStockByBranch(branchId)
        ));
    }

    @PutMapping("/branch/{branchId}/restore-all")
    public ResponseEntity<ResponseSuccess<Integer>> restoreAllStock(
            @PathVariable Long branchId) {
        int count = branchDishStockService.restoreAllStock(branchId);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Mở bán lại " + count + " món thành công",
                count
        ));
    }
}
