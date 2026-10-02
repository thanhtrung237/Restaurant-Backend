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
import com.group.restaurant.services.KitchenTicketService;
import com.group.restaurant.services.OrderNotificationService;
import com.group.restaurant.services.OrderService;
import com.group.restaurant.services.promotion.PromotionEngine;
import com.group.restaurant.services.promotion.PromotionResult;
import com.group.restaurant.utils.TenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final TableSessionRepository tableSessionRepository;
    private final AccountRepository accountRepository;
    private final DishRepository dishRepository;
    private final PromotionRepository promotionRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final DishOverrideRepository dishOverrideRepository;
    private final OptionItemRepository optionItemRepository;
    private final OrderDetailOptionRepository orderDetailOptionRepository;
    private final OrderMapper orderMapper;
    private final KitchenTicketService kitchenTicketService;
    private final OrderNotificationService orderNotificationService;
    private final PromotionEngine promotionEngine;
    private final TenantGuard tenantGuard;

    @Override
    public OrderResponse createOrder(OrderAddRequest request) {
        TableSession session = tableSessionRepository.findById(request.getTableSessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phiên làm việc với ID: " + request.getTableSessionId()));

        // chặn tạo order khi phiên đang CHỜ THANH TOÁN, ĐÃ ĐÓNG, ĐÃ HỦY
        if (session.getStatus() == TableSessionStatus.WAITING_FOR_PAY) {
            throw new ConflictException("Phiên đang chờ thanh toán, không thể gọi thêm món. Quản lý chi nhánh cần mở lại bàn trước");
        }
        if (session.getStatus() == TableSessionStatus.CLOSED
                || session.getStatus() == TableSessionStatus.CANCELLED) {
            throw new ConflictException("Phiên làm việc đã kết thúc, không thể tạo đơn hàng mới");
        }
        if (session.getStatus() != TableSessionStatus.SERVING) {
            throw new ConflictException("Phiên chưa được mở, không thể gọi món");
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

        Long branchId = session.getTable().getBranch().getId();

        List<OrderDetail> details = new ArrayList<>();
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (OrderDetailAddRequest itemReq : request.getItems()) {
                OrderDetail detail = orderDetailRepository.save(buildDetail(savedOrder, itemReq, branchId));
                saveDetailOptions(detail, itemReq);
                details.add(detail);
            }
        }
        savedOrder.setOrderDetails(details);

        // A3: Push thông báo order mới đến Phục vụ theo chi nhánh (T-01)
        orderNotificationService.notifyNewOrder(
                branchId,
                savedOrder.getId(),
                session.getId(),
                session.getTable().getName());

        return orderMapper.toResponse(savedOrder);
    }

    // Phục vụ xác nhận order: tất cả PENDING → PREPARING (không áp cho PENDING_WEIGHT)
    @Override
    public OrderResponse confirmOrder(Long id) {
        Order order = findOrderById(id);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ConflictException("Chỉ có thể xác nhận order đang ở trạng thái PENDING");
        }

        List<OrderDetail> preparingDetails = new ArrayList<>();
        for (OrderDetail detail : order.getOrderDetails()) {
            if (detail.getStatus() == OrderDetailStatus.PENDING) {
                detail.setStatus(OrderDetailStatus.PREPARING);
                orderDetailRepository.save(detail);
                preparingDetails.add(detail);
            }
            // PENDING_WEIGHT giữ nguyên, chờ phục vụ cân
        }

        order.setStatus(OrderStatus.CONFIRMED);
        OrderResponse response = orderMapper.toResponse(orderRepository.save(order));

        // FR-SRV-07: In phiếu bếp sau khi lưu
        kitchenTicketService.printKitchenTickets(id, preparingDetails);

        // A3: Push trạng thái PREPARING cho từng dòng món (T-01)
        Long sessionId = order.getTableSession().getId();
        for (OrderDetail d : preparingDetails) {
            orderNotificationService.notifyItemStatusChanged(sessionId, d.getId(), OrderDetailStatus.PREPARING);
        }

        return response;
    }

    @Override
    public OrderResponse updateOrder(Long id, OrderUpdateRequest request) {
        Order order = findOrderById(id);
        orderMapper.updateEntity(order, request);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        return orderMapper.toResponse(findOrderById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByTableSession(Long tableSessionId, OrderStatus status) {
        if (!tableSessionRepository.existsById(tableSessionId)) {
            throw new ResourceNotFoundException("Không tìm thấy phiên làm việc với ID: " + tableSessionId);
        }
        List<Order> orders = status != null
                ? orderRepository.findByTableSessionIdAndStatus(tableSessionId, status)
                : orderRepository.findByTableSessionId(tableSessionId);
        return orders.stream().map(orderMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByBranch(Long branchId, OrderStatus status) {
        tenantGuard.assertBranch(branchId);
        List<Order> orders = orderRepository.findByBranchIdAndStatus(branchId,
                status != null ? status : OrderStatus.PENDING);
        return orders.stream().map(orderMapper::toResponse).collect(Collectors.toList());
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
                if (detail.getStatus() == OrderDetailStatus.PENDING
                        || detail.getStatus() == OrderDetailStatus.PENDING_WEIGHT) {
                    detail.setStatus(OrderDetailStatus.CANCELLED);
                    detail.setIsBillable(false);
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
            order.getOrderDetails().forEach(d -> {
                d.softDelete(null);
                orderDetailRepository.save(d);
            });
        }
        order.softDelete(null);
        orderRepository.save(order);
    }


    private Order findOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + id));
    }

    private OrderDetail buildDetail(Order order, OrderDetailAddRequest req, Long branchId) {
        Dish dish = dishRepository.findById(req.getDishId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món với ID: " + req.getDishId()));

        // ưu tiên giá bản đè chi nhánh
        BigDecimal unitPrice = dishOverrideRepository.findByBranchIdAndDishId(branchId, dish.getId())
                .filter(ov -> !Boolean.TRUE.equals(ov.getIsHidden()))
                .map(ov -> ov.getOverridePrice() != null ? ov.getOverridePrice() : dish.getBasePrice())
                .orElse(dish.getBasePrice());

        // Cộng tổng giá phụ thu options (BR-07)
        BigDecimal optionExtraTotal = BigDecimal.ZERO;
        if (req.getOptionItemIds() != null && !req.getOptionItemIds().isEmpty()) {
            optionExtraTotal = req.getOptionItemIds().stream()
                    .map(optId -> optionItemRepository.findById(optId)
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy option item ID: " + optId)))
                    .map(OptionItem::getExtraPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        boolean soldByWeight = Boolean.TRUE.equals(dish.getSoldByWeight());
        OrderDetailStatus status = soldByWeight
                ? OrderDetailStatus.PENDING_WEIGHT
                : OrderDetailStatus.PENDING;

        // Món cân: chưa biết số cân → tạm price = 0, chưa tính KM
        if (soldByWeight) {
            return OrderDetail.builder()
                    .order(order)
                    .dish(dish)
                    .promotion(null)
                    .quantity(req.getQuantity())
                    .weightKg(null)
                    .priceAtOrderTime(unitPrice)
                    .promotionDiscountAmount(BigDecimal.ZERO)
                    .isTakeAway(Boolean.TRUE.equals(req.getIsTakeAway()))
                    .note(req.getNote())
                    .status(status)
                    .price(BigDecimal.ZERO)
                    .isBillable(true)
                    .build();
        }

        // Món thường: tính giá trước KM
        BigDecimal itemSubtotal = unitPrice.add(optionExtraTotal)
                .multiply(BigDecimal.valueOf(req.getQuantity()));

        // Tính khuyến mãi CẤP MÓN (tự động, không lấy từ client)
        Long restaurantId = order.getTableSession().getTable().getBranch().getRestaurant().getId();

        PromotionResult promoResult = promotionEngine.calculateItemPromotion(
                branchId,
                restaurantId,
                dish.getId(),
                dish.getCategory() != null ? dish.getCategory().getId() : null,
                req.getQuantity(),
                itemSubtotal.doubleValue()
        );

        Promotion appliedPromotion = null;
        BigDecimal discountAmount = BigDecimal.ZERO;

        // Trừ lượt nếu có KM (BR-23)
        if (promoResult.hasPromotion()) {
            boolean success = promotionEngine.incrementUsageCount(promoResult.getPromotionId());

            if (success) {
                // Trừ lượt thành công
                appliedPromotion = promotionRepository.findById(promoResult.getPromotionId()).orElse(null);
                discountAmount = promoResult.getDiscountAmount();

                log.info("Applied promotion {} to dish {}: discount = {}",
                        promoResult.getPromotionName(), dish.getName(), discountAmount);
            } else {
                // Hết lượt đúng lúc gọi món (FR-CUS-05, T-09)
                throw new ConflictException(
                        String.format("Khuyến mãi '%s' vừa hết lượt. " +
                                        "Giá mới: %,.0fđ. Vui lòng xác nhận tiếp tục.",
                                promoResult.getPromotionName(),
                                itemSubtotal.doubleValue())
                );
            }
        }

        // Giá cuối = subtotal - discount
        BigDecimal finalPrice = itemSubtotal.subtract(discountAmount);

        return OrderDetail.builder()
                .order(order)
                .dish(dish)
                .promotion(appliedPromotion)
                .quantity(req.getQuantity())
                .weightKg(null)
                .priceAtOrderTime(unitPrice)
                .promotionDiscountAmount(discountAmount)
                .isTakeAway(Boolean.TRUE.equals(req.getIsTakeAway()))
                .note(req.getNote())
                .status(status)
                .price(finalPrice)
                .isBillable(true)
                .build();
    }

    // Lưu OrderDetailOption với priceSnapshot theo BR-07
    private void saveDetailOptions(OrderDetail detail, OrderDetailAddRequest req) {
        List<Long> ids = req.getOptionItemIds();
        if (ids == null || ids.isEmpty()) return;
        for (Long optId : ids) {
            OptionItem item = optionItemRepository.findById(optId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy option item ID: " + optId));
            OrderDetailOption opt = OrderDetailOption.builder()
                    .orderDetail(detail)
                    .optionItem(item)
                    .priceSnapshot(item.getExtraPrice())
                    .build();
            orderDetailOptionRepository.save(opt);
        }
    }
}