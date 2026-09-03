package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.orderDetail.OrderDetailAddRequest;
import com.group.restaurant.dtos.request.orderDetail.OrderDetailUpdateRequest;
import com.group.restaurant.dtos.response.orderDetail.OrderDetailResponse;
import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.OrderDetailMapper;
import com.group.restaurant.models.Dish;
import com.group.restaurant.models.Order;
import com.group.restaurant.models.OrderDetail;
import com.group.restaurant.models.Promotion;
import com.group.restaurant.repositories.DishRepository;
import com.group.restaurant.repositories.OrderDetailRepository;
import com.group.restaurant.repositories.OrderRepository;
import com.group.restaurant.repositories.PromotionRepository;
import com.group.restaurant.services.OrderDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderDetailServiceImpl implements OrderDetailService {

    private final OrderDetailRepository orderDetailRepository;
    private final OrderRepository orderRepository;
    private final DishRepository dishRepository;
    private final PromotionRepository promotionRepository;
    private final OrderDetailMapper orderDetailMapper;

    @Override
    public OrderDetailResponse addOrderDetail(Long orderId, OrderDetailAddRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + orderId));

        Dish dish = dishRepository.findById(request.getDishId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với ID: " + request.getDishId()));

        Promotion promotion = null;
        if (request.getPromotionId() != null) {
            promotion = promotionRepository.findById(request.getPromotionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khuyến mãi với ID: " + request.getPromotionId()));
        }

        BigDecimal unitPrice = dish.getBasePrice();
        BigDecimal discount = BigDecimal.ZERO;

        BigDecimal multiplier = BigDecimal.valueOf(request.getQuantity());
        if (Boolean.TRUE.equals(dish.getSoldByWeight()) && request.getWeightKg() != null) {
            multiplier = request.getWeightKg();
        }

        BigDecimal totalPrice = unitPrice.multiply(multiplier).subtract(discount);
        if (totalPrice.compareTo(BigDecimal.ZERO) < 0) {
            totalPrice = BigDecimal.ZERO;
        }

        OrderDetail orderDetail = OrderDetail.builder()
                .order(order)
                .dish(dish)
                .promotion(promotion)
                .quantity(request.getQuantity())
                .weightKg(request.getWeightKg())
                .priceAtOrderTime(unitPrice)
                .promotionDiscountAmount(discount)
                .isTakeAway(Boolean.TRUE.equals(request.getIsTakeAway()))
                .note(request.getNote())
                .status(OrderDetailStatus.PENDING)
                .price(totalPrice)
                .build();

        OrderDetail savedDetail = orderDetailRepository.save(orderDetail);
        return orderDetailMapper.toResponse(savedDetail);
    }

    @Override
    public OrderDetailResponse updateOrderDetail(Long id, OrderDetailUpdateRequest request) {
        OrderDetail orderDetail = findOrderDetailById(id);

        orderDetailMapper.updateEntity(orderDetail, request);

        BigDecimal unitPrice = orderDetail.getPriceAtOrderTime();
        BigDecimal discount = orderDetail.getPromotionDiscountAmount() != null ? orderDetail.getPromotionDiscountAmount() : BigDecimal.ZERO;
        BigDecimal multiplier = BigDecimal.valueOf(orderDetail.getQuantity());
        if (orderDetail.getDish() != null && Boolean.TRUE.equals(orderDetail.getDish().getSoldByWeight()) && orderDetail.getWeightKg() != null) {
            multiplier = orderDetail.getWeightKg();
        }

        BigDecimal totalPrice = unitPrice.multiply(multiplier).subtract(discount);
        if (totalPrice.compareTo(BigDecimal.ZERO) < 0) {
            totalPrice = BigDecimal.ZERO;
        }
        orderDetail.setPrice(totalPrice);

        OrderDetail updatedDetail = orderDetailRepository.save(orderDetail);
        return orderDetailMapper.toResponse(updatedDetail);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetailById(Long id) {
        OrderDetail orderDetail = findOrderDetailById(id);
        return orderDetailMapper.toResponse(orderDetail);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDetailResponse> getOrderDetailsByOrder(Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + orderId);
        }

        List<OrderDetail> details = orderDetailRepository.findByOrderId(orderId);
        return details.stream()
                .map(orderDetailMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDetailResponse> getOrderDetailsByTableSession(Long tableSessionId, OrderDetailStatus status) {
        List<OrderDetail> details = (status != null)
                ? orderDetailRepository.findByTableSessionIdAndStatus(tableSessionId, status)
                : orderDetailRepository.findByTableSessionId(tableSessionId);

        return details.stream()
                .map(orderDetailMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void changeOrderDetailStatus(Long id, OrderDetailStatus status) {
        OrderDetail orderDetail = findOrderDetailById(id);
        orderDetail.setStatus(status);
        orderDetailRepository.save(orderDetail);
    }

    @Override
    public void deleteOrderDetail(Long id) {
        OrderDetail orderDetail = findOrderDetailById(id);
        orderDetail.softDelete(null);
        orderDetailRepository.save(orderDetail);
    }

    private OrderDetail findOrderDetailById(Long id) {
        return orderDetailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi tiết đơn hàng với ID: " + id));
    }
}
