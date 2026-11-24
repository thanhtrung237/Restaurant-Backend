package com.group.restaurant.services;

import com.group.restaurant.dtos.request.order.OrderAddRequest;
import com.group.restaurant.dtos.request.orderDetail.OrderDetailAddRequest;
import com.group.restaurant.enums.*;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.models.Invoice;
import com.group.restaurant.models.OrderDetail;
import com.group.restaurant.models.TableSession;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.InvoiceRepository;
import com.group.restaurant.repositories.OrderDetailRepository;
import com.group.restaurant.repositories.TableSessionRepository;
import com.group.restaurant.utils.TenantGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TakeawayService {
    private final BranchRepository branches;
    private final TableSessionRepository sessions;
    private final OrderDetailRepository details;
    private final InvoiceRepository invoices;
    private final OrderService orders;
    private final OrderNotificationService notifications;
    private final TenantGuard tenantGuard;

    public record Create(
            @Size(max = 100) String customerName,
            @NotEmpty List<@Valid OrderDetailAddRequest> items) {}

    public enum KitchenStatus {
        EMPTY,
        PENDING,
        PREPARING,
        READY
    }

    public record Entry(
            Long id,
            String label,
            String customerName,
            LocalDateTime openedAt,
            int portions,
            KitchenStatus kitchenStatus,
            boolean paid,
            Long invoiceId,
            TableSessionStatus sessionStatus,
            boolean canAddTakeawayItems) {}

    @Transactional
    public Entry create(Long branchId, Create request) {
        tenantGuard.assertBranchAssigned(branchId);
        var branch =
                branches.findById(branchId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Không tìm thấy chi nhánh."));
        if (branch.getStatus() != BranchStatus.ACTIVE
                || branch.getRestaurant().getStatus() != RestaurantStatus.ACTIVE) {
            throw new ConflictException("Chi nhánh hoặc nhà hàng đang ngừng hoạt động.");
        }
        TableSession session =
                sessions.save(
                        TableSession.builder()
                                .branch(branch)
                                .status(TableSessionStatus.SERVING)
                                .openedAt(LocalDateTime.now())
                                .vatRateAtOpen(branch.getVatRate())
                                .serviceFeeRateAtOpen(BigDecimal.ZERO)
                                .coverFeePerGuestAtOpen(BigDecimal.ZERO)
                                .customerName(
                                        request.customerName() == null
                                                ? null
                                                : request.customerName().trim())
                                .build());
        OrderAddRequest order = new OrderAddRequest();
        order.setTableSessionId(session.getId());
        order.setItems(request.items());
        orders.createOrder(order);
        return describe(session);
    }

    @Transactional(readOnly = true)
    public List<Entry> list(Long branchId) {
        tenantGuard.assertBranchAssigned(branchId);
        return sessions
                .findByBranchIdAndTableIsNullAndPickedUpAtIsNullAndStatusNotOrderByIdAsc(
                        branchId, TableSessionStatus.CANCELLED)
                .stream()
                .map(this::describe)
                .toList();
    }

    @Transactional
    public void handoff(Long branchId, Long id) {
        tenantGuard.assertBranchAssigned(branchId);
        TableSession session =
                sessions.findTakeawayForUpdate(id)
                        .filter(found -> branchId.equals(found.getBranch().getId()))
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Không tìm thấy đơn mang về."));
        if (session.getPickedUpAt() != null) return;
        Entry state = describe(session);
        if (session.getStatus() == TableSessionStatus.CANCELLED
                || !state.paid()
                || state.kitchenStatus() != KitchenStatus.READY) {
            throw new ConflictException("Đơn cần hoàn tất chế biến và thanh toán trước khi giao.");
        }
        session.setPickedUpAt(LocalDateTime.now());
        notifications.notifyTakeawayUpdated(session);
    }

    private Entry describe(TableSession session) {
        List<OrderDetail> items =
                details.findByTableSessionId(session.getId()).stream()
                        .filter(detail -> detail.getStatus() != OrderDetailStatus.CANCELLED)
                        .toList();
        int portions = items.stream().mapToInt(OrderDetail::getQuantity).sum();
        KitchenStatus kitchen =
                items.isEmpty()
                        ? KitchenStatus.EMPTY
                        : items.stream()
                                        .allMatch(
                                                item ->
                                                        item.getStatus()
                                                                == OrderDetailStatus.SERVED)
                                ? KitchenStatus.READY
                                : items.stream()
                                                .anyMatch(
                                                        item ->
                                                                item.getStatus()
                                                                                == OrderDetailStatus
                                                                                        .PREPARING
                                                                        || item.getStatus()
                                                                                == OrderDetailStatus
                                                                                        .SERVED)
                                        ? KitchenStatus.PREPARING
                                        : KitchenStatus.PENDING;
        Invoice invoice = invoices.findByTableSessionId(session.getId()).orElse(null);
        boolean paid =
                invoice != null
                        && (invoice.getStatus() == InvoiceStatus.PAID
                                || invoice.getStatus() == InvoiceStatus.ISSUED);
        return new Entry(
                session.getId(),
                session.getDisplayName(),
                session.getCustomerName(),
                session.getOpenedAt(),
                portions,
                kitchen,
                paid,
                invoice == null ? null : invoice.getId(),
                session.getStatus(),
                session.canAddTakeawayItems(invoice != null));
    }
}
