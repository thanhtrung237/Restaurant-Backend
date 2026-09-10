package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.orderDetail.OrderDetailAddRequest;
import com.group.restaurant.dtos.request.orderDetail.OrderDetailUpdateRequest;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.orderDetail.OrderDetailResponse;
import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.services.OrderDetailService;
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
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Thay đổi trạng thái món ăn trong đơn thành công",
                null
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
}
