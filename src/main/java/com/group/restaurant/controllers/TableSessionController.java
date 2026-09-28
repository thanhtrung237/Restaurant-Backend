package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.tableSession.*;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.tableSession.TableSessionResponse;
import com.group.restaurant.services.TableSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/table-sessions")
@RequiredArgsConstructor
public class TableSessionController {

    private final TableSessionService tableSessionService;

    /**
        Quét QR → tạo phiên WAITING_FOR_OPEN
        Đặt bàn trước → tạo phiên RESERVED (khi có reservationTime)
     */
    @PostMapping("")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> createSession(
            @Valid @RequestBody TableSessionCreateRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Tạo phiên bàn thành công",
                tableSessionService.createSession(request)
        ));
    }

    /**
        Phục vụ xác nhận mở bàn → SERVING
        Dùng cho cả WAITING_FOR_OPEN và RESERVED
     */
    @PostMapping("/{id}/confirm-open")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> confirmOpen(
            @PathVariable Long id,
            @Valid @RequestBody TableSessionConfirmOpenRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Mở bàn thành công",
                tableSessionService.confirmOpen(id, request)
        ));
    }

    /**
     * Hủy đặt bàn trước: RESERVED → CANCELLED
     */
    @PostMapping("/{id}/cancel-reservation")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> cancelReservation(
            @PathVariable Long id,
            @RequestBody(required = false) TableSessionCancelReservationRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Hủy đặt bàn trước thành công",
                tableSessionService.cancelReservation(id, request)
        ));
    }

    /**
     * Yêu cầu thanh toán: SERVING → WAITING_FOR_PAY
     */
    @PostMapping("/{id}/request-payment")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> requestPayment(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Chuyển bàn sang trạng thái chờ thanh toán thành công",
                tableSessionService.requestPayment(id)
        ));
    }

    /**
     * QL chi nhánh mở lại bàn: WAITING_FOR_PAY → SERVING
     */
    @PostMapping("/{id}/reopen")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> reopenSession(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Mở lại bàn thành công",
                tableSessionService.reopenSession(id)
        ));
    }

    @PostMapping("/legacy")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> openSession(
            @Valid @RequestBody TableSessionOpenRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Mở phiên làm việc cho bàn thành công",
                tableSessionService.openSession(request)
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> updateSession(
            @PathVariable Long id,
            @Valid @RequestBody TableSessionUpdateRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Cập nhật thông tin phiên làm việc thành công",
                tableSessionService.updateSession(id, request)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> getSessionById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy thông tin phiên bàn thành công",
                tableSessionService.getSessionById(id)
        ));
    }

    @GetMapping("/table/{tableId}/open")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> getOpenSessionByTable(
            @PathVariable Long tableId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy phiên làm việc đang mở của bàn thành công",
                tableSessionService.getOpenSessionByTable(tableId)
        ));
    }

    @GetMapping("/branch/{branchId}/open")
    public ResponseEntity<ResponseSuccess<List<TableSessionResponse>>> getOpenSessionsByBranch(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách các phiên làm việc đang mở theo chi nhánh thành công",
                tableSessionService.getOpenSessionsByBranch(branchId)
        ));
    }

    @GetMapping("/table/{tableId}")
    public ResponseEntity<ResponseSuccess<List<TableSessionResponse>>> getSessionsByTable(
            @PathVariable Long tableId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy lịch sử các phiên làm việc của bàn thành công",
                tableSessionService.getSessionsByTable(tableId)
        ));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> closeSession(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Đóng phiên làm việc bàn thành công",
                tableSessionService.closeSession(id)
        ));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> cancelSession(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Hủy phiên làm việc bàn thành công",
                tableSessionService.cancelSession(id)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseSuccess<Void>> deleteSession(@PathVariable Long id) {
        tableSessionService.deleteSession(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Xóa phiên bàn thành công",
                null
        ));
    }
}
