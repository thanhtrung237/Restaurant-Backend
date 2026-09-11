package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.invoice.InvoiceCreateRequest;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.invoice.InvoiceListResponse;
import com.group.restaurant.dtos.response.invoice.InvoiceResponse;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.services.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping("")
    public ResponseEntity<ResponseSuccess<InvoiceResponse>> createInvoice(
            @Valid @RequestBody InvoiceCreateRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Tạo hóa đơn thành công",
                invoiceService.createInvoice(request)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<InvoiceResponse>> getInvoiceById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy thông tin hóa đơn thành công",
                invoiceService.getInvoiceById(id)
        ));
    }

    @GetMapping("/session/{tableSessionId}")
    public ResponseEntity<ResponseSuccess<InvoiceResponse>> getInvoiceByTableSession(
            @PathVariable Long tableSessionId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy hóa đơn theo phiên làm việc thành công",
                invoiceService.getInvoiceByTableSession(tableSessionId)
        ));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ResponseSuccess<List<InvoiceListResponse>>> getInvoicesByBranch(
            @PathVariable Long branchId,
            @RequestParam(required = false) InvoiceStatus status) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách hóa đơn theo chi nhánh thành công",
                invoiceService.getInvoicesByBranch(branchId, status)
        ));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ResponseSuccess<Void>> cancelInvoice(@PathVariable Long id) {
        invoiceService.cancelInvoice(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Hủy hóa đơn thành công",
                null
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseSuccess<Void>> deleteInvoice(@PathVariable Long id) {
        invoiceService.deleteInvoice(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Xóa hóa đơn thành công",
                null
        ));
    }
}
