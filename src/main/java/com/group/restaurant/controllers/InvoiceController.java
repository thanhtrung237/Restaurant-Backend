package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.invoice.InvoiceCreateRequest;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.invoice.InvoiceListResponse;
import com.group.restaurant.dtos.response.invoice.InvoiceResponse;
import com.group.restaurant.dtos.response.invoice.QrCodeResponse;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.services.InvoiceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.math.BigDecimal;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    public record ShareQuoteRequest(@NotNull @Positive BigDecimal expectedTotal) {}

    @PostMapping("/{id}/share-quote")
    public ResponseEntity<Void> shareQuote(@PathVariable Long id, @Valid @RequestBody ShareQuoteRequest request) {
        invoiceService.shareQuote(id, request.expectedTotal());
        return ResponseEntity.noContent().build();
    }

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

  
    @PostMapping("/{id}/issue")
    public ResponseEntity<ResponseSuccess<InvoiceResponse>> issueEInvoice(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Phát hành hóa đơn điện tử thành công",
                invoiceService.issueEInvoice(id)
        ));
    }

    @GetMapping("/{id}/qr-code")
    public ResponseEntity<ResponseSuccess<QrCodeResponse>> generateQrCode(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Sinh QR thanh toán thành công",
                invoiceService.generateQrCode(id)
        ));
    }
    
    @PostMapping("/session/{tableSessionId}/qr-opened")
    public ResponseEntity<Void> reportGuestQrOpened(@PathVariable Long tableSessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken,
            @Valid @RequestBody(required = false) ShareQuoteRequest request) {
        invoiceService.reportGuestQrOpened(tableSessionId, guestToken, request == null ? null : request.expectedTotal());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session/{tableSessionId}/qr-code")
    public ResponseEntity<ResponseSuccess<QrCodeResponse>> generateQrCodeByTableSession(
            @PathVariable Long tableSessionId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy mã QR thanh toán theo phiên bàn thành công",
                invoiceService.generateQrCodeByTableSession(tableSessionId)
        ));
    }
}
