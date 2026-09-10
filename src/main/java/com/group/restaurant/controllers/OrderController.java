package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.order.OrderAddRequest;
import com.group.restaurant.dtos.request.order.OrderUpdateRequest;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.order.OrderResponse;
import com.group.restaurant.enums.OrderStatus;
import com.group.restaurant.services.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("")
    public ResponseEntity<ResponseSuccess<OrderResponse>> createOrder(
            @Valid @RequestBody OrderAddRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Tạo đơn hàng thành công",
                orderService.createOrder(request)
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseSuccess<OrderResponse>> updateOrder(
            @PathVariable Long id,
            @Valid @RequestBody OrderUpdateRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Cập nhật đơn hàng thành công",
                orderService.updateOrder(id, request)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<OrderResponse>> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy thông tin đơn hàng thành công",
                orderService.getOrderById(id)
        ));
    }

    @GetMapping("/session/{tableSessionId}")
    public ResponseEntity<ResponseSuccess<List<OrderResponse>>> getOrdersByTableSession(
            @PathVariable Long tableSessionId,
            @RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách đơn hàng theo phiên làm việc thành công",
                orderService.getOrdersByTableSession(tableSessionId, status)
        ));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ResponseSuccess<List<OrderResponse>>> getOrdersByBranch(
            @PathVariable Long branchId,
            @RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách đơn hàng theo chi nhánh thành công",
                orderService.getOrdersByBranch(branchId, status)
        ));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ResponseSuccess<Void>> changeOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status) {
        orderService.changeOrderStatus(id, status);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Thay đổi trạng thái đơn hàng thành công",
                null
        ));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ResponseSuccess<Void>> cancelOrder(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Hủy đơn hàng thành công",
                null
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseSuccess<Void>> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Xóa đơn hàng thành công",
                null
        ));
    }
}
