package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.manualDiscount.ManualDiscountAddRequest;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.manualDiscount.ManualDiscountResponse;
import com.group.restaurant.services.ManualDiscountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/manual-discounts")
@RequiredArgsConstructor
public class ManualDiscountController {

    private final ManualDiscountService manualDiscountService;

    @PostMapping("")
    public ResponseEntity<ResponseSuccess<ManualDiscountResponse>> createManualDiscount(
            @Valid @RequestBody ManualDiscountAddRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Tạo giảm giá thủ công thành công",
                manualDiscountService.createManualDiscount(request)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<ManualDiscountResponse>> getManualDiscountById(
            @PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy giảm giá thủ công thành công",
                manualDiscountService.getManualDiscountById(id)
        ));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ResponseSuccess<List<ManualDiscountResponse>>> getByOrder(
            @PathVariable Long orderId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách giảm giá theo đơn hàng thành công",
                manualDiscountService.getByOrder(orderId)
        ));
    }

    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<ResponseSuccess<List<ManualDiscountResponse>>> getByInvoice(
            @PathVariable Long invoiceId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách giảm giá theo hóa đơn thành công",
                manualDiscountService.getByInvoice(invoiceId)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseSuccess<Void>> deleteManualDiscount(@PathVariable Long id) {
        manualDiscountService.deleteManualDiscount(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Xóa giảm giá thủ công thành công",
                null
        ));
    }
}
