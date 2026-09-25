package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.orderDetail.*;
import com.group.restaurant.dtos.response.orderDetail.OrderDetailOptionResponse;
import com.group.restaurant.dtos.response.orderDetail.OrderDetailResponse;
import com.group.restaurant.enums.CancelReason;
import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.OrderDetailMapper;
import com.group.restaurant.models.*;
import com.group.restaurant.repositories.*;
import com.group.restaurant.services.KitchenTicketService;
import com.group.restaurant.services.OrderDetailService;
import com.group.restaurant.services.OrderNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final DishOverrideRepository dishOverrideRepository;
    private final OrderDetailOptionRepository orderDetailOptionRepository;
    private final KitchenTicketService kitchenTicketService;
    private final OrderNotificationService orderNotificationService;
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

        // lấy giá từ bản đè chi nhánh nếu có, không thì lấy giá gốc
        Long branchId = order.getTableSession().getTable().getBranch().getId();
        BigDecimal unitPrice = resolvePrice(dish, branchId);

        OrderDetailStatus initialStatus = Boolean.TRUE.equals(dish.getSoldByWeight())
                ? OrderDetailStatus.PENDING_WEIGHT   // Món cân → CHỜ CÂN
                : OrderDetailStatus.PENDING;         // Món thường → CHỜ XÁC NHẬN

        // Món cân: price chốt sau khi cân, tạm để 0
        BigDecimal totalPrice = Boolean.TRUE.equals(dish.getSoldByWeight())
                ? BigDecimal.ZERO
                : unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

        OrderDetail orderDetail = OrderDetail.builder()
                .order(order)
                .dish(dish)
                .promotion(promotion)
                .quantity(request.getQuantity())
                .weightKg(null) // chưa có số cân
                .priceAtOrderTime(unitPrice)
                .promotionDiscountAmount(BigDecimal.ZERO)
                .isTakeAway(Boolean.TRUE.equals(request.getIsTakeAway()))
                .note(request.getNote())
                .status(initialStatus)
                .price(totalPrice)
                .isBillable(true)
                .build();

        return toResponseWithOptions(orderDetailRepository.save(orderDetail));
    }


    // Phục vụ nhập số cân: PENDING_WEIGHT → PREPARING 
    @Override
    public OrderDetailResponse confirmWeight(Long id, OrderDetailConfirmWeightRequest request) {
        OrderDetail detail = findById(id);

        if (detail.getStatus() != OrderDetailStatus.PENDING_WEIGHT) {
            throw new ConflictException("Chỉ có thể nhập số cân cho món đang ở trạng thái CHỜ CÂN");
        }

        // Cộng option extra price snapshot (BR-07)
        BigDecimal optionExtra = orderDetailOptionRepository.findByOrderDetailId(id)
                .stream()
                .map(opt -> opt.getPriceSnapshot() != null ? opt.getPriceSnapshot() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Chốt thành tiền = (đơn giá/kg + option extra) × số cân thật
        BigDecimal totalPrice = detail.getPriceAtOrderTime().add(optionExtra).multiply(request.getWeightKg());

        detail.setWeightKg(request.getWeightKg());
        detail.setPrice(totalPrice);
        detail.setStatus(OrderDetailStatus.PREPARING);

        OrderDetailResponse response = orderDetailMapper.toResponse(orderDetailRepository.save(detail));

        // FR-SRV-07: In phiếu bếp sau khi cân xong
        kitchenTicketService.printKitchenTickets(detail.getOrder().getId(), List.of(detail));

        // A3: Push trạng thái mới đến khách/phục vụ (T-01)
        Long sessionId = detail.getOrder().getTableSession().getId();
        orderNotificationService.notifyItemStatusChanged(sessionId, id, OrderDetailStatus.PREPARING);

        return response;
    }

    // Phục vụ xác nhận đã mang món ra: PREPARING → SERVED
    @Override
    public OrderDetailResponse markAsServed(Long id) {
        OrderDetail detail = findById(id);

        if (detail.getStatus() != OrderDetailStatus.PREPARING) {
            throw new ConflictException("Chỉ có thể xác nhận phục vụ cho món đang ở trạng thái ĐANG CHẾ BIẾN");
        }

        detail.setStatus(OrderDetailStatus.SERVED);
        OrderDetailResponse response = orderDetailMapper.toResponse(orderDetailRepository.save(detail));

        // A3: Push trạng thái SERVED (T-01)
        Long sessionId = detail.getOrder().getTableSession().getId();
        orderNotificationService.notifyItemStatusChanged(sessionId, id, OrderDetailStatus.SERVED);

        return response;
    }

    // Hủy trước bếp: PENDING / PENDING_WEIGHT → CANCELLED, không tính tiền 
    @Override
    public OrderDetailResponse cancelBeforeKitchen(Long id) {
        OrderDetail detail = findById(id);

        if (detail.getStatus() != OrderDetailStatus.PENDING
                && detail.getStatus() != OrderDetailStatus.PENDING_WEIGHT) {
            throw new ConflictException("Chỉ có thể hủy tự do với món đang CHỜ XÁC NHẬN hoặc CHỜ CÂN");
        }

        detail.setStatus(OrderDetailStatus.CANCELLED);
        detail.setIsBillable(false); // không tính tiền
        detail.setCancelledAt(LocalDateTime.now());

        OrderDetailResponse response = orderDetailMapper.toResponse(orderDetailRepository.save(detail));

        // A3: Push trạng thái HỦY (T-01)
        Long sessionId = detail.getOrder().getTableSession().getId();
        orderNotificationService.notifyItemStatusChanged(sessionId, id, OrderDetailStatus.CANCELLED);

        return response;
    }

    // Hủy sau bếp: PREPARING / SERVED → CANCELLED với lý do 
    @Override
    public OrderDetailResponse cancelAfterKitchen(Long id, OrderDetailCancelRequest request) {
        OrderDetail detail = findById(id);

        if (detail.getStatus() != OrderDetailStatus.PREPARING
                && detail.getStatus() != OrderDetailStatus.SERVED) {
            throw new ConflictException("Chỉ có thể hủy sau bếp với món đang ĐANG CHẾ BIẾN hoặc ĐÃ PHỤC VỤ");
        }

        //lý do quyết định có tính tiền không
        boolean isBillable = request.getCancelReason() == CancelReason.CUSTOMER_CHANGED_MIND;

        detail.setStatus(OrderDetailStatus.CANCELLED);
        detail.setCancelReason(request.getCancelReason());
        detail.setIsBillable(isBillable);
        detail.setCancelledAt(LocalDateTime.now());

        OrderDetailResponse response = orderDetailMapper.toResponse(orderDetailRepository.save(detail));

        // FR-SRV-07, BR-24: In phiếu hủy gửi bếp
        kitchenTicketService.printCancelTicket(detail);

        // A3: Push trạng thái HỦY (T-01)
        Long sessionId = detail.getOrder().getTableSession().getId();
        orderNotificationService.notifyItemStatusChanged(sessionId, id, OrderDetailStatus.CANCELLED);

        return response;
    }

    @Override
    public OrderDetailResponse updateOrderDetail(Long id, OrderDetailUpdateRequest request) {
        OrderDetail detail = findById(id);

        // Chỉ cho sửa khi còn ở CHỜ XÁC NHẬN
        if (detail.getStatus() != OrderDetailStatus.PENDING) {
            throw new ConflictException("Chỉ có thể cập nhật món đang ở trạng thái CHỜ XÁC NHẬN");
        }

        orderDetailMapper.updateEntity(detail, request);

        // Tính lại giá
        BigDecimal multiplier = BigDecimal.valueOf(detail.getQuantity());
        BigDecimal totalPrice = detail.getPriceAtOrderTime().multiply(multiplier);
        detail.setPrice(totalPrice);

        return orderDetailMapper.toResponse(orderDetailRepository.save(detail));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetailById(Long id) {
        return toResponseWithOptions(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDetailResponse> getOrderDetailsByOrder(Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + orderId);
        }
        return orderDetailRepository.findByOrderId(orderId)
                .stream().map(this::toResponseWithOptions).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDetailResponse> getOrderDetailsByTableSession(Long tableSessionId, OrderDetailStatus status) {
        List<OrderDetail> details = status != null
                ? orderDetailRepository.findByTableSessionIdAndStatus(tableSessionId, status)
                : orderDetailRepository.findByTableSessionId(tableSessionId);
        return details.stream().map(this::toResponseWithOptions).collect(Collectors.toList());
    }

    @Override
    public void changeOrderDetailStatus(Long id, OrderDetailStatus status) {
        OrderDetail detail = findById(id);
        detail.setStatus(status);
        orderDetailRepository.save(detail);
    }

    @Override
    public void deleteOrderDetail(Long id) {
        OrderDetail detail = findById(id);
        detail.softDelete(null);
        orderDetailRepository.save(detail);
    }


    private OrderDetail findById(Long id) {
        return orderDetailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dòng món với ID: " + id));
    }

    /**
        Lấy giá hiệu lực của món tại chi nhánh
        Nếu có bản đè chi nhánh và có giá đè → lấy giá đè
        Nếu không → lấy giá gốc của món
     */
    private BigDecimal resolvePrice(Dish dish, Long branchId) {
        return dishOverrideRepository.findByBranchIdAndDishId(branchId, dish.getId())
                .filter(override -> !Boolean.TRUE.equals(override.getIsHidden()))
                .map(override -> override.getOverridePrice() != null ? override.getOverridePrice() : dish.getBasePrice())
                .orElse(dish.getBasePrice());
    }

    /**
     * Helper method để map OrderDetail -> OrderDetailResponse và enrich với options
     */
    private OrderDetailResponse toResponseWithOptions(OrderDetail orderDetail) {
        OrderDetailResponse response = orderDetailMapper.toResponse(orderDetail);
        enrichWithOptions(response);
        return response;
    }

    /**
     * Enrich OrderDetailResponse với danh sách options đã chọn
     */
    private void enrichWithOptions(OrderDetailResponse response) {
        List<OrderDetailOption> options = orderDetailOptionRepository.findByOrderDetailId(response.getId());
        List<OrderDetailOptionResponse> optionResponses = options.stream()
                .map(opt -> OrderDetailOptionResponse.builder()
                        .id(opt.getId())
                        .optionItemId(opt.getOptionItem().getId())
                        .optionItemName(opt.getOptionItem().getName())
                        .priceSnapshot(opt.getPriceSnapshot())
                        .build())
                .collect(Collectors.toList());
        response.setOptions(optionResponses);
    }
}
