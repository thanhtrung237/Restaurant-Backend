package com.group.restaurant.controllers;

import com.group.restaurant.dtos.response.base.ApiResponse;
import com.group.restaurant.models.OrderDetail;
import com.group.restaurant.repositories.OrderDetailRepository;
import com.group.restaurant.services.KitchenTicketService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * FR-SRV-07: Endpoint để reprint phiếu bếp theo order nếu cần.
 */
@RestController
@RequestMapping("/api/v1/kitchen-tickets")
@RequiredArgsConstructor
public class KitchenTicketController {

    private final KitchenTicketService kitchenTicketService;
    private final OrderDetailRepository orderDetailRepository;

    /**
     * Reprint phiếu bếp cho một order (Phục vụ bấm in lại).
     * Chỉ in những dòng đang PREPARING.
     */
    @PostMapping("/reprint/{orderId}")
    public ResponseEntity<ApiResponse<String>> reprintByOrder(@PathVariable Long orderId) {
        List<OrderDetail> preparingDetails =
                orderDetailRepository.findByOrderIdAndStatus(
                        orderId, com.group.restaurant.enums.OrderDetailStatus.PREPARING);

        kitchenTicketService.printKitchenTickets(orderId, preparingDetails);
        return ResponseEntity.ok(
                ApiResponse.success("Đã gửi lại phiếu bếp cho order #" + orderId, null));
    }
}
