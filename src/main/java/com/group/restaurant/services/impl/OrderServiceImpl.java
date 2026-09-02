package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.order.OrderAddRequest;
import com.group.restaurant.dtos.request.order.OrderUpdateRequest;
import com.group.restaurant.dtos.request.orderDetail.OrderDetailAddRequest;
import com.group.restaurant.dtos.response.order.OrderResponse;
import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.enums.OrderStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.OrderMapper;
import com.group.restaurant.models.*;
import com.group.restaurant.repositories.*;
import com.group.restaurant.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final TableSessionRepository tableSessionRepository;
    private final AccountRepository accountRepository;
    private final DishRepository dishRepository;
    private final PromotionRepository promotionRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final OrderMapper orderMapper;

    @Override
    public OrderResponse createOrder(OrderAddRequest request) {
        TableSession session = tableSessionRepository.findById(request.getTableSessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phiên làm việc với ID: " + request.getTableSessionId()));

        if (session.getStatus() == TableSessionStatus.CLOSED || session.getStatus() == TableSessionStatus.CANCELLED) {
            throw new ConflictException("Phiên làm việc này đã đóng hoặc đã bị hủy, không thể tạo đơn hàng mới");
        }

        Account orderedBy = null;
        if (request.getOrderedById() != null) {
            orderedBy = accountRepository.findById(request.getOrderedById())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy tài khoản nhân viên với ID: " + request.getOrderedById()));
        }

        Order order = Order.builder()
                .tableSession(session)
                .orderedBy(orderedBy)
                .note(request.getNote())
                .status(OrderStatus.PENDING)
                .orderDetails(new ArrayList<>())
                .build();

        Order savedOrder = orderRepository.save(order);

        List<OrderDetail> details = new ArrayList<>();
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (OrderDetailAddRequest itemReq : request.getItems()) {
                OrderDetail detail = buildOrderDetailEntity(savedOrder, itemReq);
                details.add(orderDetailRepository.save(detail));
            }
        }
        savedOrder.setOrderDetails(details);

        return orderMapper.toResponse(savedOrder);
    }

    @Override
    public OrderResponse updateOrder(Long id, OrderUpdateRequest request) {
        Order order = findOrderById(id);
        orderMapper.updateEntity(order, request);
        Order updatedOrder = orderRepository.save(order);
        return orderMapper.toResponse(updatedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = findOrderById(id);
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByTableSession(Long tableSessionId, OrderStatus status) {
        if (!tableSessionRepository.existsById(tableSessionId)) {
            throw new ResourceNotFoundException("Không tìm thấy phiên làm việc với ID: " + tableSessionId);
        }

        List<Order> orders = (status != null)
                ? orderRepository.findByTableSessionIdAndStatus(tableSessionId, status)
                : orderRepository.findByTableSessionId(tableSessionId);

        return orders.stream()
                .map(orderMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByBranch(Long branchId, OrderStatus status) {
        List<Order> orders = orderRepository.findByBranchIdAndStatus(branchId,
                status != null ? status : OrderStatus.PENDING);
        return orders.stream()
                .map(orderMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void changeOrderStatus(Long id, OrderStatus status) {
        Order order = findOrderById(id);
        order.setStatus(status);
        orderRepository.save(order);
    }

    @Override
    public void cancelOrder(Long id) {
        Order order = findOrderById(id);
        order.setStatus(OrderStatus.CANCELLED);

        if (order.getOrderDetails() != null) {
            for (OrderDetail detail : order.getOrderDetails()) {
                if (detail.getStatus() == OrderDetailStatus.PENDING) {
                    detail.setStatus(OrderDetailStatus.CANCELLED);
                    orderDetailRepository.save(detail);
                }
            }
        }
        orderRepository.save(order);
    }

    @Override
    public void deleteOrder(Long id) {
        Order order = findOrderById(id);
        if (order.getOrderDetails() != null) {
            for (OrderDetail detail : order.getOrderDetails()) {
                detail.softDelete(null);
                orderDetailRepository.save(detail);
            }
        }
        order.softDelete(null);
        orderRepository.save(order);
    }

    private Order findOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + id));
    }

    private OrderDetail buildOrderDetailEntity(Order order, OrderDetailAddRequest itemReq) {
        Dish dish = dishRepository.findById(itemReq.getDishId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy món ăn với ID: " + itemReq.getDishId()));

        Promotion promotion = null;
        if (itemReq.getPromotionId() != null) {
            promotion = promotionRepository.findById(itemReq.getPromotionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy khuyến mãi với ID: " + itemReq.getPromotionId()));
        }

        BigDecimal unitPrice = dish.getBasePrice();
        BigDecimal discount = BigDecimal.ZERO;

        BigDecimal multiplier = BigDecimal.valueOf(itemReq.getQuantity());
        if (Boolean.TRUE.equals(dish.getSoldByWeight()) && itemReq.getWeightKg() != null) {
            multiplier = itemReq.getWeightKg();
        }

        BigDecimal totalPrice = unitPrice.multiply(multiplier).subtract(discount);
        if (totalPrice.compareTo(BigDecimal.ZERO) < 0) {
            totalPrice = BigDecimal.ZERO;
        }

        return OrderDetail.builder()
                .order(order)
                .dish(dish)
                .promotion(promotion)
                .quantity(itemReq.getQuantity())
                .weightKg(itemReq.getWeightKg())
                .priceAtOrderTime(unitPrice)
                .promotionDiscountAmount(discount)
                .isTakeAway(Boolean.TRUE.equals(itemReq.getIsTakeAway()))
                .note(itemReq.getNote())
                .status(OrderDetailStatus.PENDING)
                .price(totalPrice)
                .build();
    }
}
