package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.orderDetail.*;
import com.group.restaurant.dtos.response.orderDetail.OrderDetailOptionResponse;
import com.group.restaurant.dtos.response.orderDetail.OrderDetailResponse;
import com.group.restaurant.enums.CancelReason;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.enums.OperationalPermission;
import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.OrderDetailMapper;
import com.group.restaurant.models.*;
import com.group.restaurant.models.TableSession;
import com.group.restaurant.repositories.*;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.InvoiceService;
import com.group.restaurant.services.KitchenTicketService;
import com.group.restaurant.services.OperationalPermissionService;
import com.group.restaurant.services.OrderDetailService;
import com.group.restaurant.services.OrderNotificationService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.services.promotion.PromotionEngine;
import com.group.restaurant.services.promotion.PromotionResult;
import com.group.restaurant.utils.TenantGuard;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final OptionItemRepository optionItemRepository;
    private final KitchenTicketService kitchenTicketService;
    private final OrderNotificationService orderNotificationService;
    private final OrderDetailMapper orderDetailMapper;
    private final AuditLogService auditLogService;
    private final TenantGuard tenantGuard;
    private final OperationalPermissionService permissions;
    private final PromotionEngine promotionEngine;
    private final TableSessionRepository tableSessionRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;

    @Override
    public OrderDetailResponse addOrderDetail(Long orderId, OrderDetailAddRequest request) {
        Order order =
                orderRepository
                        .findById(orderId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy đơn hàng với ID: " + orderId));
        tenantGuard.assertBranch(
                order.getTableSession() != null ? order.getTableSession().getBranch() : null);

        // Cung bo dieu kien voi OrderServiceImpl.createOrder. Thieu chot nay thi them
        // duoc mon vao hoa don SAU khi khach da chot bill (be-issues muc 18).
        TableSession sessionOfOrder = order.getTableSession();
        if (sessionOfOrder != null) {
            if (sessionOfOrder.getStatus() == TableSessionStatus.WAITING_FOR_PAY) {
                throw new ConflictException(
                        "Phiên đang chờ thanh toán, không thể gọi thêm món. "
                                + "Quản lý chi nhánh cần mở lại bàn trước");
            }
            if (sessionOfOrder.getStatus() == TableSessionStatus.CLOSED
                    || sessionOfOrder.getStatus() == TableSessionStatus.CANCELLED) {
                throw new ConflictException("Phiên làm việc đã kết thúc, không thể thêm món");
            }
            if (sessionOfOrder.getStatus() != TableSessionStatus.SERVING) {
                throw new ConflictException("Phiên chưa được mở, không thể gọi món");
            }
        }

        Dish dish =
                dishRepository
                        .findById(request.getDishId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy món ăn với ID: "
                                                        + request.getDishId()));

        // Giá hiệu lực tại chi nhánh
        Branch branchOfOrder = order.getTableSession().getBranch();
        Long branchId = branchOfOrder.getId();
        Long restaurantId = branchOfOrder.getRestaurant().getId();
        BigDecimal unitPrice = resolvePrice(dish, branchId);

        // Topping đã chọn (BR-07): lấy giá hiện tại làm snapshot
        List<OptionItem> optionItems = new java.util.ArrayList<>();
        if (request.getOptionItemIds() != null) {
            for (Long optId : request.getOptionItemIds()) {
                optionItems.add(
                        optionItemRepository
                                .findById(optId)
                                .orElseThrow(
                                        () ->
                                                new ResourceNotFoundException(
                                                        "Không tìm thấy option item ID: "
                                                                + optId)));
            }
        }
        BigDecimal optionExtra =
                optionItems.stream()
                        .map(o -> o.getExtraPrice() != null ? o.getExtraPrice() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean soldByWeight = Boolean.TRUE.equals(dish.getSoldByWeight());
        OrderDetailStatus initialStatus =
                soldByWeight
                        ? OrderDetailStatus.PENDING_WEIGHT // Món cân → CHỜ CÂN
                        : OrderDetailStatus.PENDING; // Món thường → CHỜ XÁC NHẬN

        // QUY ƯỚC price: luôn là số SAU khuyến mãi món và ĐÃ GỒM topping,
        //   price = (đơn giá + topping) × SL|kg − promotionDiscountAmount
        // Món cân: chưa có số cân nên tạm 0, chốt ở confirmWeight.
        OrderDetail orderDetail =
                OrderDetail.builder()
                        .order(order)
                        .dish(dish)
                        .promotion(null)
                        .quantity(request.getQuantity())
                        .weightKg(null) // chưa có số cân
                        .priceAtOrderTime(unitPrice)
                        .promotionDiscountAmount(BigDecimal.ZERO)
                        .isTakeAway(Boolean.TRUE.equals(request.getIsTakeAway()))
                        .note(request.getNote())
                        .status(initialStatus)
                        .price(BigDecimal.ZERO)
                        .isBillable(true)
                        .build();

        if (!soldByWeight) {
            BigDecimal gross =
                    unitPrice.add(optionExtra).multiply(BigDecimal.valueOf(request.getQuantity()));
            BigDecimal discount =
                    applyAutoItemPromotion(
                            orderDetail,
                            dish,
                            branchId,
                            restaurantId,
                            request.getQuantity(),
                            gross);
            orderDetail.setPrice(gross.subtract(discount));
        }

        // Khuyến mãi món do hệ thống tự áp (BR-22), không lấy từ client. Chỉ giữ liên kết KM do
        // client gửi (hành vi cũ, discount = 0) khi hệ thống không tự áp được KM nào.
        if (orderDetail.getPromotion() == null && request.getPromotionId() != null) {
            orderDetail.setPromotion(
                    promotionRepository
                            .findById(request.getPromotionId())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Không tìm thấy khuyến mãi với ID: "
                                                            + request.getPromotionId())));
        }

        OrderDetail saved = orderDetailRepository.save(orderDetail);
        for (OptionItem item : optionItems) {
            orderDetailOptionRepository.save(
                    OrderDetailOption.builder()
                            .orderDetail(saved)
                            .optionItem(item)
                            .priceSnapshot(item.getExtraPrice())
                            .build());
        }

        orderNotificationService.notifySessionUpdated(saved.getOrder().getTableSession());
        return toResponseWithOptions(saved);
    }

    // Phục vụ nhập số cân: PENDING_WEIGHT → PREPARING
    @Override
    public OrderDetailResponse confirmWeight(Long id, OrderDetailConfirmWeightRequest request) {
        OrderDetail detail = findForUpdate(id);
        requirePermission(detail, OperationalPermission.WEIGH_ORDER);

        if (detail.getStatus() != OrderDetailStatus.PENDING_WEIGHT) {
            throw new ConflictException("Chỉ có thể nhập số cân cho món đang ở trạng thái CHỜ CÂN");
        }

        // Cộng option extra price snapshot (BR-07)
        BigDecimal optionExtra =
                orderDetailOptionRepository.findByOrderDetailId(id).stream()
                        .map(
                                opt ->
                                        opt.getPriceSnapshot() != null
                                                ? opt.getPriceSnapshot()
                                                : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Thành tiền GỐC = (đơn giá/kg + option extra) × số cân thật
        BigDecimal grossPrice =
                detail.getPriceAtOrderTime().add(optionExtra).multiply(request.getWeightKg());

        detail.setWeightKg(request.getWeightKg());

        // BR-22: Tính khuyến mãi món cân sau khi đã có số cân (chỉ percentage).
        // QUY ƯỚC price: số SAU khuyến mãi (giống buildDetail) — trước đây ở đây price bị để là số
        // trước KM dù vẫn ghi promotionDiscountAmount, làm tổng lệch.
        Dish dish = detail.getDish();
        Branch branchOfDetail = detail.getOrder().getTableSession().getBranch();
        BigDecimal discount =
                applyAutoItemPromotion(
                        detail,
                        dish,
                        branchOfDetail.getId(),
                        branchOfDetail.getRestaurant().getId(),
                        request.getWeightKg().intValue(),
                        grossPrice);
        detail.setPrice(grossPrice.subtract(discount));

        detail.setStatus(OrderDetailStatus.PREPARING);

        OrderDetailResponse response =
                orderDetailMapper.toResponse(orderDetailRepository.save(detail));

        // FR-SRV-07: In phiếu bếp sau khi cân xong
        kitchenTicketService.printKitchenTickets(detail.getOrder().getId(), List.of(detail));

        // A3: Push trạng thái mới đến khách/phục vụ (T-01)
        notifyDetailStatus(detail);

        return response;
    }

    // Ghi nhận số phần đã mang ra và chuyển trạng thái khi đủ.
    @Override
    public OrderDetailResponse markAsServed(Long id, OrderDetailServeRequest request) {
        OrderDetail detail = findForUpdate(id);
        requirePermission(detail, OperationalPermission.SERVE_ORDER);

        if (detail.getStatus() != OrderDetailStatus.PREPARING) {
            throw new ConflictException(
                    "Chỉ có thể xác nhận phục vụ cho món đang ở trạng thái ĐANG CHẾ BIẾN");
        }

        int served = detail.getServedQuantity();
        int remaining = detail.getQuantity() - served;
        if (request != null && request.expectedServedQuantity() != served) {
            throw new ConflictException("Số lượng đã phục vụ vừa thay đổi. Vui lòng kiểm tra lại.");
        }
        int servingNow = request == null ? remaining : request.quantity();
        if (servingNow > remaining) {
            throw new ConflictException("Số lượng phục vụ vượt quá số phần còn lại.");
        }
        detail.setServedQuantity(served + servingNow);
        if (servingNow == remaining) {
            detail.setStatus(OrderDetailStatus.SERVED);
            notifyDetailStatus(detail);
        } else {
            orderNotificationService.notifySessionUpdated(detail.getOrder().getTableSession());
        }

        return orderDetailMapper.toResponse(detail);
    }

    // Hủy trước bếp: PENDING / PENDING_WEIGHT → CANCELLED, không tính tiền
    @Override
    public OrderDetailResponse cancelBeforeKitchen(Long id) {
        OrderDetail detail = findById(id);
        assertOrderEditable(detail);
        return cancelBeforeKitchen(detail);
    }

    private OrderDetailResponse cancelBeforeKitchen(OrderDetail detail) {
        if (detail.getStatus() != OrderDetailStatus.PENDING
                && detail.getStatus() != OrderDetailStatus.PENDING_WEIGHT) {
            throw new ConflictException(
                    "Chỉ có thể hủy tự do với món đang CHỜ XÁC NHẬN hoặc CHỜ CÂN");
        }

        // BR-23: Refund promotion usage nếu món này có khuyến mãi
        if (detail.getPromotion() != null
                && detail.getPromotionDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            promotionEngine.decrementUsageCount(detail.getPromotion().getId());
        }

        detail.setStatus(OrderDetailStatus.CANCELLED);
        detail.setIsBillable(false); // không tính tiền
        detail.setCancelledAt(LocalDateTime.now());

        OrderDetailResponse response =
                orderDetailMapper.toResponse(orderDetailRepository.save(detail));

        // A3: Push trạng thái HỦY (T-01)
        notifyDetailStatus(detail);

        return response;
    }

    // Hủy sau bếp: PREPARING / SERVED → CANCELLED với lý do
    @Override
    public OrderDetailResponse cancelAfterKitchen(Long id, OrderDetailCancelRequest request) {
        OrderDetail detail = findForUpdate(id);
        assertOrderEditable(detail);
        return cancelAfterKitchen(detail, request.getCancelReason());
    }

    private OrderDetailResponse cancelAfterKitchen(OrderDetail detail, CancelReason reason) {
        if (detail.getStatus() != OrderDetailStatus.PREPARING
                && detail.getStatus() != OrderDetailStatus.SERVED) {
            throw new ConflictException(
                    "Chỉ có thể hủy sau bếp với món đang ĐANG CHẾ BIẾN hoặc ĐÃ PHỤC VỤ");
        }

        // lý do quyết định có tính tiền không
        boolean isBillable = reason == CancelReason.CUSTOMER_CHANGED_MIND;
        OrderDetailStatus previousStatus = detail.getStatus();

        // BR-24: Refund promotion usage chỉ khi lỗi nhà hàng (không tính tiền khách)
        if (!isBillable
                && detail.getPromotion() != null
                && detail.getPromotionDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            promotionEngine.decrementUsageCount(detail.getPromotion().getId());
        }

        detail.setStatus(OrderDetailStatus.CANCELLED);
        detail.setCancelReason(reason);
        detail.setIsBillable(isBillable);
        detail.setCancelledAt(LocalDateTime.now());

        OrderDetailResponse response =
                orderDetailMapper.toResponse(orderDetailRepository.save(detail));

        // FR-LOG-01 + BR-24: huỷ món sau bếp quyết định CÓ/KHÔNG tính tiền khách,
        // nên phải ghi rõ lý do và kết luận billable để đối soát khi khách khiếu nại.
        TableSession session = detail.getOrder().getTableSession();
        auditLogService.record(
                AuditEvent.builder()
                        .entityType("OrderDetail")
                        .entityId(detail.getId())
                        .actionType(AuditAction.CANCEL)
                        .branchId(
                                session != null && session.getBranch() != null
                                        ? session.getBranch().getId()
                                        : null)
                        .valueBefore("status=" + previousStatus + "; isBillable=true")
                        .valueAfter(
                                "status="
                                        + OrderDetailStatus.CANCELLED
                                        + "; isBillable="
                                        + isBillable
                                        + "; cancelReason="
                                        + reason
                                        + "; dish="
                                        + detail.getDish().getName()
                                        + "; price="
                                        + detail.getPrice())
                        .reason(
                                isBillable
                                        ? "Huỷ sau bếp — khách đổi ý, VẪN TÍNH TIỀN (BR-24)"
                                        : "Huỷ sau bếp — lỗi nhà hàng, KHÔNG tính tiền")
                        .build());

        // FR-SRV-07, BR-24: In phiếu hủy gửi bếp
        kitchenTicketService.printCancelTicket(detail);

        // A3: Push trạng thái HỦY (T-01)
        notifyDetailStatus(detail);

        return response;
    }

    @Override
    public OrderDetailResponse removeFromCheckout(Long sessionId, Long detailId, String reason) {
        TableSession session =
                tableSessionRepository
                        .findForUpdate(sessionId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy phiên tính tiền."));
        tenantGuard.assertBranchAssigned(
                session.getBranch().getId(),
                TenantGuard.ROLE_CASHIER,
                TenantGuard.ROLE_BRANCH_MANAGER,
                TenantGuard.ROLE_MANAGER);
        if (session.getStatus() != TableSessionStatus.WAITING_FOR_PAY) {
            throw new ConflictException("Chỉ được điều chỉnh khi phiên đang chờ thanh toán.");
        }
        Invoice invoice = invoiceRepository.findBySessionForPayment(sessionId).orElse(null);
        if (invoice != null
                && (invoice.getStatus() != InvoiceStatus.PENDING
                        || paymentRepository.sumAmountByInvoiceId(invoice.getId()).signum() > 0)) {
            throw new ConflictException("Hóa đơn đã ghi nhận thanh toán hoặc đã khóa.");
        }
        boolean sharedQuote = invoice != null && invoice.getQuoteSharedAt() != null;
        if (sharedQuote) {
            tenantGuard.assertBranchAssigned(
                    session.getBranch().getId(), TenantGuard.ROLE_BRANCH_MANAGER);
            if (reason == null || reason.isBlank()) {
                throw new InvalidParamException("Nhập lý do khi bỏ món sau khi đã gửi tạm tính.");
            }
        }

        OrderDetail detail = findForUpdate(detailId);
        if (!sessionId.equals(detail.getOrder().getTableSession().getId())) {
            throw new ResourceNotFoundException("Không tìm thấy món trong phiên tính tiền.");
        }
        var response =
                switch (detail.getStatus()) {
                    case PENDING, PENDING_WEIGHT -> cancelBeforeKitchen(detail);
                    case PREPARING, SERVED -> {
                        tenantGuard.assertBranchAssigned(
                                session.getBranch().getId(),
                                TenantGuard.ROLE_BRANCH_MANAGER,
                                TenantGuard.ROLE_MANAGER);
                        yield cancelAfterKitchen(detail, CancelReason.RESTAURANT_ERROR);
                    }
                    default -> throw new ConflictException("Món này đã được hủy.");
                };
        if (invoice != null) invoiceService.recalculateAfterItemRemoval(invoice.getId());
        if (sharedQuote) {
            auditLogService.record(
                    AuditEvent.builder()
                            .entityType("Invoice")
                            .entityId(invoice.getId())
                            .branchId(session.getBranch().getId())
                            .actionType(AuditAction.UPDATE)
                            .valueAfter(
                                    "removedDetailId="
                                            + detailId
                                            + "; grandTotal="
                                            + invoice.getGrandTotal())
                            .reason(reason.trim())
                            .build());
        }
        return response;
    }

    @Override
    public OrderDetailResponse updateOrderDetail(Long id, OrderDetailUpdateRequest request) {
        OrderDetail detail = findById(id);
        assertOrderEditable(detail);

        // Chỉ cho sửa khi còn ở CHỜ XÁC NHẬN
        if (detail.getStatus() != OrderDetailStatus.PENDING) {
            throw new ConflictException("Chỉ có thể cập nhật món đang ở trạng thái CHỜ XÁC NHẬN");
        }

        orderDetailMapper.updateEntity(detail, request);

        // Tính lại giá theo quy ước: price = (đơn giá + topping) × SL − KM món.
        // Số lượng đổi thì KM món cũng phải tính lại: hoàn lượt KM cũ (BR-23) rồi áp lại.
        if (detail.getPromotion() != null
                && detail.getPromotionDiscountAmount() != null
                && detail.getPromotionDiscountAmount().signum() > 0) {
            promotionEngine.decrementUsageCount(detail.getPromotion().getId());
        }
        detail.setPromotion(null);
        detail.setPromotionDiscountAmount(BigDecimal.ZERO);

        BigDecimal optionExtra = optionExtraOf(detail.getId());
        BigDecimal gross =
                detail.getPriceAtOrderTime()
                        .add(optionExtra)
                        .multiply(BigDecimal.valueOf(detail.getQuantity()));
        Branch branchOfDetail = detail.getOrder().getTableSession().getBranch();
        BigDecimal discount =
                applyAutoItemPromotion(
                        detail,
                        detail.getDish(),
                        branchOfDetail.getId(),
                        branchOfDetail.getRestaurant().getId(),
                        detail.getQuantity(),
                        gross);
        detail.setPrice(gross.subtract(discount));

        OrderDetail saved = orderDetailRepository.save(detail);
        orderNotificationService.notifySessionUpdated(saved.getOrder().getTableSession());
        return orderDetailMapper.toResponse(saved);
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
        return orderDetailRepository.findByOrderId(orderId).stream()
                .map(this::toResponseWithOptions)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDetailResponse> getOrderDetailsByTableSession(
            Long tableSessionId, OrderDetailStatus status) {
        List<OrderDetail> details =
                status != null
                        ? orderDetailRepository.findByTableSessionIdAndStatus(
                                tableSessionId, status)
                        : orderDetailRepository.findByTableSessionId(tableSessionId);
        return details.stream().map(this::toResponseWithOptions).collect(Collectors.toList());
    }

    @Override
    public void changeOrderDetailStatus(Long id, OrderDetailStatus status) {
        OrderDetail detail = findById(id);
        if (status == OrderDetailStatus.PREPARING)
            requirePermission(detail, OperationalPermission.CONFIRM_ORDER);
        if (status == OrderDetailStatus.SERVED)
            requirePermission(detail, OperationalPermission.SERVE_ORDER);
        if (detail.getOrder().getTableSession().isTakeaway()) {
            throw new ConflictException(
                    "Hãy dùng thao tác xác nhận món hoặc đóng gói tại màn bếp.");
        }
        detail.setStatus(status);
        orderDetailRepository.save(detail);

        Long sessionId =
                detail.getOrder() != null && detail.getOrder().getTableSession() != null
                        ? detail.getOrder().getTableSession().getId()
                        : null;
        if (sessionId != null) {
            notifyDetailStatus(detail);
        }
    }

    @Override
    public List<OrderDetailResponse> batchConfirm(OrderDetailBatchConfirmRequest request) {
        if (request == null
                || request.getOrderDetailIds() == null
                || request.getOrderDetailIds().isEmpty()) {
            return List.of();
        }

        List<OrderDetailResponse> responses = new java.util.ArrayList<>();
        List<OrderDetail> newlyConfirmedDetails = new java.util.ArrayList<>();

        var details = request.getOrderDetailIds().stream().distinct().map(this::findById).toList();
        details.stream()
                .map(detail -> detail.getOrder().getTableSession().getBranch().getId())
                .distinct()
                .forEach(
                        branchId ->
                                permissions.require(branchId, OperationalPermission.CONFIRM_ORDER));
        for (OrderDetail detail : details) {
            // Chỉ xác nhận các món đang ở trạng thái PENDING
            if (detail.getStatus() == OrderDetailStatus.PENDING) {
                detail.setStatus(OrderDetailStatus.PREPARING);
                OrderDetail saved = orderDetailRepository.save(detail);
                responses.add(toResponseWithOptions(saved));
                newlyConfirmedDetails.add(saved);

                Long sessionId =
                        saved.getOrder() != null && saved.getOrder().getTableSession() != null
                                ? saved.getOrder().getTableSession().getId()
                                : null;
                if (sessionId != null) {
                    orderNotificationService.notifyItemStatusChanged(
                            sessionId, saved.getId(), OrderDetailStatus.PREPARING);
                }
            } else {
                responses.add(toResponseWithOptions(detail));
            }
        }

        // FR-SRV-07: In phiếu bếp cho các món vừa được xác nhận (gom theo từng order)
        if (!newlyConfirmedDetails.isEmpty()) {
            newlyConfirmedDetails.stream()
                    .map(detail -> detail.getOrder().getTableSession())
                    .distinct()
                    .forEach(orderNotificationService::notifySessionUpdated);
            Map<Long, List<OrderDetail>> byOrder =
                    newlyConfirmedDetails.stream()
                            .filter(d -> d.getOrder() != null && d.getOrder().getId() != null)
                            .collect(Collectors.groupingBy(d -> d.getOrder().getId()));

            byOrder.forEach(
                    (orderId, items) -> {
                        kitchenTicketService.printKitchenTickets(orderId, items);
                    });
        }

        return responses;
    }

    private void requirePermission(OrderDetail detail, OperationalPermission permission) {
        permissions.require(detail.getOrder().getTableSession().getBranch().getId(), permission);
    }

    private void notifyDetailStatus(OrderDetail detail) {
        TableSession session = detail.getOrder().getTableSession();
        orderNotificationService.notifyItemStatusChanged(
                session.getId(), detail.getId(), detail.getStatus());
        orderNotificationService.notifySessionUpdated(session);
    }

    @Override
    public void deleteOrderDetail(Long id) {
        OrderDetail detail = findById(id);
        assertOrderEditable(detail);
        detail.softDelete(null);
        orderDetailRepository.save(detail);
        orderNotificationService.notifySessionUpdated(detail.getOrder().getTableSession());
    }

    /**
     * FR-CUS-07: Khách bấm "Báo nhân viên" cho một món đang PREPARING.
     * Push STAFF_CALL đến topic của chi nhánh.
     */
    public void reportToStaff(Long id) {
        OrderDetail detail =
                orderDetailRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy dòng món với ID: " + id));
        if (detail.getStatus() != OrderDetailStatus.PREPARING) {
            throw new ConflictException("Chỉ có thể báo nhân viên cho món đang Đang chế biến");
        }
        TableSession session = detail.getOrder().getTableSession();
        Long branchId = session.getBranch() != null ? session.getBranch().getId() : null;
        if (branchId != null) {
            String tableNo = session.getTable() != null ? session.getTable().getName() : "";
            String dishName = detail.getDish() != null ? detail.getDish().getName() : "";
            orderNotificationService.notifyStaffCall(
                    branchId, session.getId(), id, tableNo, dishName);
        }
    }

    /**
     * Chốt tenant tại finder dùng chung — phủ confirmWeight / markAsServed /
     * cancelBeforeKitchen / cancelAfterKitchen / updateOrderDetail /
     * changeOrderDetailStatus / deleteOrderDetail / getOrderDetailById.
     * No-op với khách ẩn danh (khách tự huỷ món chưa vào bếp qua endpoint công khai).
     */
    private OrderDetail findById(Long id) {
        return checkDetailBranch(id, orderDetailRepository.findById(id));
    }

    private OrderDetail findForUpdate(Long id) {
        return checkDetailBranch(id, orderDetailRepository.findForUpdate(id));
    }

    private OrderDetail checkDetailBranch(Long id, Optional<OrderDetail> found) {
        OrderDetail detail =
                found.orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Không tìm thấy dòng món với ID: " + id));
        TableSession session =
                detail.getOrder() != null ? detail.getOrder().getTableSession() : null;
        if (session != null) {
            tenantGuard.assertBranchAssigned(session.getBranch().getId());
        }
        return detail;
    }

    private void assertOrderEditable(OrderDetail detail) {
        TableSession session = detail.getOrder().getTableSession();
        if (session.isTakeaway() && session.getStatus() != TableSessionStatus.SERVING) {
            throw new ConflictException("Đơn mang về đã chốt thanh toán, không thể sửa món.");
        }
        if (session.getStatus() == TableSessionStatus.WAITING_FOR_PAY) {
            throw new ConflictException(
                    "Đơn đang chờ thanh toán. Hãy điều chỉnh món trên màn chốt bàn.");
        }
    }

    /** Tổng phụ thu topping đã snapshot của một dòng món (BR-07). */
    private BigDecimal optionExtraOf(Long orderDetailId) {
        return orderDetailOptionRepository.findByOrderDetailId(orderDetailId).stream()
                .map(
                        opt ->
                                opt.getPriceSnapshot() != null
                                        ? opt.getPriceSnapshot()
                                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Tự áp khuyến mãi CẤP MÓN tốt nhất (BR-22, BR-23) lên dòng: ghi promotion và
     * promotionDiscountAmount, trừ lượt KM. Trả số tiền giảm (0 nếu không có KM hoặc KM vừa hết
     * lượt), đã kẹp không vượt quá `gross`. Bên gọi tự đặt price = gross − số trả về.
     */
    private BigDecimal applyAutoItemPromotion(
            OrderDetail detail,
            Dish dish,
            Long branchId,
            Long restaurantId,
            int quantity,
            BigDecimal gross) {
        PromotionResult promoResult =
                promotionEngine.calculateItemPromotion(
                        branchId,
                        restaurantId,
                        dish.getId(),
                        dish.getCategory() != null ? dish.getCategory().getId() : null,
                        quantity,
                        gross.doubleValue());

        if (promoResult.hasPromotion()
                && promotionEngine.incrementUsageCount(promoResult.getPromotionId())) {
            BigDecimal discount = promoResult.getDiscountAmount().min(gross).max(BigDecimal.ZERO);
            detail.setPromotion(
                    promotionRepository.findById(promoResult.getPromotionId()).orElse(null));
            detail.setPromotionDiscountAmount(discount);
            return discount;
        }
        // Không có KM, hoặc KM vừa hết lượt → không giảm
        detail.setPromotion(null);
        detail.setPromotionDiscountAmount(BigDecimal.ZERO);
        return BigDecimal.ZERO;
    }

    /**
     * Lấy giá hiệu lực của món tại chi nhánh
     * Nếu có bản đè chi nhánh và có giá đè → lấy giá đè
     * Nếu không → lấy giá gốc của món
     */
    private BigDecimal resolvePrice(Dish dish, Long branchId) {
        return dishOverrideRepository
                .findByBranchIdAndDishId(branchId, dish.getId())
                .filter(override -> !Boolean.TRUE.equals(override.getIsHidden()))
                .map(
                        override ->
                                override.getOverridePrice() != null
                                        ? override.getOverridePrice()
                                        : dish.getBasePrice())
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
        List<OrderDetailOption> options =
                orderDetailOptionRepository.findByOrderDetailId(response.getId());
        List<OrderDetailOptionResponse> optionResponses =
                options.stream()
                        .map(
                                opt ->
                                        OrderDetailOptionResponse.builder()
                                                .id(opt.getId())
                                                .optionItemId(opt.getOptionItem().getId())
                                                .optionItemName(opt.getOptionItem().getName())
                                                .priceSnapshot(opt.getPriceSnapshot())
                                                .build())
                        .collect(Collectors.toList());
        response.setOptions(optionResponses);
    }
}
