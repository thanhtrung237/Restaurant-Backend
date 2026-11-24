package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.request.manualDiscount.ManualDiscountAddRequest;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.invoice.InvoiceResponse;
import com.group.restaurant.dtos.response.manualDiscount.ManualDiscountResponse;
import com.group.restaurant.models.InvoiceDiscountApproval;
import com.group.restaurant.services.ManualDiscountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/manual-discounts")
@RequiredArgsConstructor
public class ManualDiscountController {

    private final ManualDiscountService manualDiscountService;

    public record ApprovalDecision(@NotBlank String requestId) {}

    @GetMapping("/invoice/{invoiceId}/approval")
    public ResponseEntity<ResponseSuccess<InvoiceDiscountApproval>> getApproval(
            @PathVariable Long invoiceId) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK, "Yêu cầu chiết khấu", manualDiscountService.getApproval(invoiceId)));
    }

    @PostMapping("/invoice/{invoiceId}/approval/{approved}")
    public ResponseEntity<ResponseSuccess<InvoiceResponse>> decideApproval(
            @PathVariable Long invoiceId,
            @PathVariable boolean approved,
            @Valid @RequestBody ApprovalDecision decision) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Đã xử lý yêu cầu chiết khấu",
                        manualDiscountService.decideApproval(
                                invoiceId, decision.requestId(), approved)));
    }

    @PostMapping("")
    public ResponseEntity<ResponseSuccess<ManualDiscountResponse>> createManualDiscount(
            @Valid @RequestBody ManualDiscountAddRequest request) {
        return ResponseEntity.status(CREATED)
                .body(
                        new ResponseSuccess<>(
                                CREATED,
                                "Tạo giảm giá thủ công thành công",
                                manualDiscountService.createManualDiscount(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<ManualDiscountResponse>> getManualDiscountById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy giảm giá thủ công thành công",
                        manualDiscountService.getManualDiscountById(id)));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ResponseSuccess<List<ManualDiscountResponse>>> getByOrder(
            @PathVariable Long orderId) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách giảm giá theo đơn hàng thành công",
                        manualDiscountService.getByOrder(orderId)));
    }

    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<ResponseSuccess<List<ManualDiscountResponse>>> getByInvoice(
            @PathVariable Long invoiceId) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách giảm giá theo hóa đơn thành công",
                        manualDiscountService.getByInvoice(invoiceId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseSuccess<Void>> deleteManualDiscount(@PathVariable Long id) {
        manualDiscountService.deleteManualDiscount(id);
        return ResponseEntity.ok(
                new ResponseSuccess<>(OK, "Xóa giảm giá thủ công thành công", null));
    }

    @PutMapping("/invoice/{invoiceId}")
    public ResponseEntity<ResponseSuccess<InvoiceResponse>> replaceInvoiceDiscount(
            @PathVariable Long invoiceId, @Valid @RequestBody ManualDiscountAddRequest request) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Đã lưu chiết khấu",
                        manualDiscountService.replaceInvoiceDiscount(invoiceId, request)));
    }

    @DeleteMapping("/invoice/{invoiceId}")
    public ResponseEntity<ResponseSuccess<InvoiceResponse>> clearInvoiceDiscount(
            @PathVariable Long invoiceId) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Đã bỏ chiết khấu",
                        manualDiscountService.clearInvoiceDiscount(invoiceId)));
    }
}
