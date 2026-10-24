package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.orderDetail.OrderDetailAddRequest;
import com.group.restaurant.dtos.request.orderDetail.OrderDetailCancelRequest;
import com.group.restaurant.dtos.request.orderDetail.OrderDetailConfirmWeightRequest;
import com.group.restaurant.dtos.request.orderDetail.OrderDetailUpdateRequest;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.orderDetail.OrderDetailResponse;
import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.services.OrderDetailService;
import com.group.restaurant.services.impl.OrderDetailServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/order-details")
@RequiredArgsConstructor
public class OrderDetailController {

    private final OrderDetailService orderDetailService;

    @PostMapping("/order/{orderId}")
    public ResponseEntity<ResponseSuccess<OrderDetailResponse>> addOrderDetail(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderDetailAddRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Thêm món vào đơn hàng thành công",
                orderDetailService.addOrderDetail(orderId, request)
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseSuccess<OrderDetailResponse>> updateOrderDetail(
            @PathVariable Long id,
            @Valid @RequestBody OrderDetailUpdateRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Cập nhật chi tiết đơn hàng thành công",
                orderDetailService.updateOrderDetail(id, request)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<OrderDetailResponse>> getOrderDetailById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy thông tin món trong đơn hàng thành công",
                orderDetailService.getOrderDetailById(id)
        ));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ResponseSuccess<List<OrderDetailResponse>>> getOrderDetailsByOrder(
            @PathVariable Long orderId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách các món theo đơn hàng thành công",
                orderDetailService.getOrderDetailsByOrder(orderId)
        ));
    }

    @GetMapping("/session/{tableSessionId}")
    public ResponseEntity<ResponseSuccess<List<OrderDetailResponse>>> getOrderDetailsByTableSession(
            @PathVariable Long tableSessionId,
            @RequestParam(required = false) OrderDetailStatus status) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách các món theo phiên bàn thành công",
                orderDetailService.getOrderDetailsByTableSession(tableSessionId, status)
        ));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ResponseSuccess<Void>> changeOrderDetailStatus(
            @PathVariable Long id,
            @RequestParam OrderDetailStatus status) {
        orderDetailService.changeOrderDetailStatus(id, status);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Thay đổi trạng thái món ăn trong đơn thành công", null));
    }

    @PostMapping("/batch-confirm")
    public ResponseEntity<ResponseSuccess<List<OrderDetailResponse>>> batchConfirm(
            @Valid @RequestBody com.group.restaurant.dtos.request.orderDetail.OrderDetailBatchConfirmRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Xác nhận các món thành công",
                orderDetailService.batchConfirm(request)
        ));
    }

    // Phục vụ nhập số cân: PENDING_WEIGHT → PREPARING
    @PostMapping("/{id}/confirm-weight")
    public ResponseEntity<ResponseSuccess<OrderDetailResponse>> confirmWeight(
            @PathVariable Long id,
            @Valid @RequestBody OrderDetailConfirmWeightRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Nhập số cân thành công, món chuyển sang ĐANG CHẾ BIẾN",
                orderDetailService.confirmWeight(id, request)
        ));
    }

    // Phục vụ xác nhận đã mang món ra: PREPARING → SERVED
    @PostMapping("/{id}/mark-served")
    public ResponseEntity<ResponseSuccess<OrderDetailResponse>> markAsServed(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Xác nhận đã phục vụ món thành công",
                orderDetailService.markAsServed(id)
        ));
    }

    // Hủy trước bếp: PENDING / PENDING_WEIGHT → CANCELLED, không tính tiền
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ResponseSuccess<OrderDetailResponse>> cancelBeforeKitchen(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Hủy món thành công",
                orderDetailService.cancelBeforeKitchen(id)
        ));
    }

    // Hủy sau bếp: PREPARING / SERVED → CANCELLED với lý do 
    @PostMapping("/{id}/cancel-after-kitchen")
    public ResponseEntity<ResponseSuccess<OrderDetailResponse>> cancelAfterKitchen(
            @PathVariable Long id,
            @Valid @RequestBody OrderDetailCancelRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Hủy món sau bếp thành công",
                orderDetailService.cancelAfterKitchen(id, request)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseSuccess<Void>> deleteOrderDetail(@PathVariable Long id) {
        orderDetailService.deleteOrderDetail(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Xóa chi tiết món thành công",
                null
        ));
    }

    // FR-CUS-07: Khách bấm "Báo nhân viên" cho món đang PREPARING — public, không cần auth
    @PostMapping("/{id}/staff-call")
    public ResponseEntity<ResponseSuccess<Void>> reportToStaff(@PathVariable Long id) {
        ((OrderDetailServiceImpl) orderDetailService).reportToStaff(id);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Đã gửi yêu cầu hỗ trợ đến nhân viên", null));
    }
}

