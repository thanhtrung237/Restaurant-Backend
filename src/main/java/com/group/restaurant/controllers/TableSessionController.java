package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.tableSession.*;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.invoice.BillPreviewResponse;
import com.group.restaurant.dtos.response.tableSession.TableSessionResponse;
import com.group.restaurant.services.BillService;
import com.group.restaurant.services.TableSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/table-sessions")
@RequiredArgsConstructor
public class TableSessionController {

    private final TableSessionService tableSessionService;
    private final BillService billService;

    /**
     Quét QR → tạo phiên WAITING_FOR_OPEN (public, không cần đăng nhập — FR-CUS-01)
     */
    @PreAuthorize("hasAnyRole('WAITER','CASHIER','BRANCH_MANAGER','MANAGER')")
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
     * Đặt bàn trước → tạo phiên RESERVED (FR-TBL-03, BR-29)
     * Chỉ Phục vụ và QL chi nhánh (Bảng 4.2)
     */
    @PreAuthorize("hasAnyRole('WAITER', 'BRANCH_MANAGER', 'MANAGER')")
    @PostMapping("/reserve")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> reserveSession(
            @Valid @RequestBody TableSessionReservationRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Đặt bàn trước thành công",
                tableSessionService.reserveSession(request)
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
     * Khách bấm "Gọi nhân viên" hỗ trợ tại bàn (FR-CUS-07)
     * orderDetailId: truyền kèm nếu khách báo nhân viên từ một món cụ thể đã vào bếp
     */
    @PostMapping("/{id}/call-staff")
    public ResponseEntity<ResponseSuccess<Void>> callStaff(
            @PathVariable Long id,
            @RequestParam(required = false) Long orderDetailId) {
        tableSessionService.callStaff(id, orderDetailId);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Đã gửi yêu cầu gọi nhân viên thành công", null));
    }

    /**
     * QL chi nhánh mở lại bàn: WAITING_FOR_PAY → SERVING
     */
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
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

    /**
     * Xem tạm tính theo phiên (BR-13) — dùng khi CHƯA có hóa đơn; đã có hóa đơn thì trả đúng số
     * của hóa đơn. Chỉ đọc. Công khai như GET order-details theo phiên (khách chưa đăng nhập).
     */
    @GetMapping("/{id}/bill-preview")
    public ResponseEntity<ResponseSuccess<BillPreviewResponse>> getBillPreview(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy tạm tính theo phiên thành công",
                billService.previewByTableSession(id)
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

    // FR-SRV-02: đóng bàn là việc của Thu ngân / QL chi nhánh (và Quản trị nhà hàng).
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER', 'CASHIER')")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> closeSession(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Đóng phiên làm việc bàn thành công",
                tableSessionService.closeSession(id)
        ));
    }

    // Bảng 2.2: huỷ phiên là việc của Quản lý chi nhánh (và Quản trị nhà hàng),
    // BẮT BUỘC nhập lý do, có ghi nhật ký. Thu ngân và Phục vụ KHÔNG được huỷ.
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> cancelSession(
            @PathVariable Long id,
            @Valid @RequestBody TableSessionCancelRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Hủy phiên làm việc bàn thành công",
                tableSessionService.cancelSession(id, request.getReason())
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

    /**
     * Chuyển bàn: di chuyển phiên sang bàn trống khác (FR-SRV-06, BR-28)
     */
    @PreAuthorize("hasAnyRole('WAITER', 'CASHIER', 'BRANCH_MANAGER', 'MANAGER')")
    @PostMapping("/{id}/transfer")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> transferTable(
            @PathVariable Long id,
            @Valid @RequestBody TableSessionTransferRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Chuyển bàn thành công",
                tableSessionService.transferTable(id, request)
        ));
    }

    /**
     * Gộp bàn: gộp phiên nguồn vào phiên đích (FR-SRV-06, BR-26, BR-28)
     */
    @PreAuthorize("hasAnyRole('WAITER', 'CASHIER', 'BRANCH_MANAGER', 'MANAGER')")
    @PostMapping("/merge")
    public ResponseEntity<ResponseSuccess<TableSessionResponse>> mergeTable(
            @Valid @RequestBody TableSessionMergeRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Gộp bàn thành công",
                tableSessionService.mergeTable(request)
        ));
    }
}
