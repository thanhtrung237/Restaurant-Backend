package com.group.restaurant.services.impl;

import com.group.restaurant.models.OrderDetail;
import com.group.restaurant.models.OrderDetailOption;
import com.group.restaurant.repositories.OrderDetailOptionRepository;
import com.group.restaurant.services.KitchenTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FR-SRV-07: In phiếu bếp — log nội dung ra console (stub),
 * tách theo kitchenStationType nếu chi nhánh nhiều khu chế biến.
 * Không in giá lên phiếu bếp.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenTicketServiceImpl implements KitchenTicketService {

    private final OrderDetailOptionRepository orderDetailOptionRepository;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    @Override
    public void printKitchenTickets(Long orderId, List<OrderDetail> preparingDetails) {
        if (preparingDetails == null || preparingDetails.isEmpty()) return;

        // Tách nhóm theo khu chế biến
        Map<String, List<OrderDetail>> byStation = preparingDetails.stream()
                .collect(Collectors.groupingBy(d ->
                        d.getDish().getKitchenStationType() != null
                                ? d.getDish().getKitchenStationType()
                                : "DEFAULT"));

        byStation.forEach((station, items) -> {
            StringBuilder ticket = buildTicket(orderId, station, items, false);
            // Stub: log ra console thay vì gửi máy in nhiệt
            log.info("[KITCHEN TICKET] Station={}\n{}", station, ticket);
        });
    }

    @Override
    public void printCancelTicket(OrderDetail cancelledDetail) {
        String station = cancelledDetail.getDish().getKitchenStationType() != null
                ? cancelledDetail.getDish().getKitchenStationType() : "DEFAULT";

        StringBuilder ticket = buildTicket(
                cancelledDetail.getOrder().getId(),
                station,
                List.of(cancelledDetail),
                true);

        log.info("[KITCHEN CANCEL TICKET] Station={}\n{}", station, ticket);
    }

    // --- helpers ---

    private StringBuilder buildTicket(Long orderId, String station,
                                      List<OrderDetail> items, boolean isCancel) {
        StringBuilder sb = new StringBuilder();
        String header = isCancel ? "*** PHIẾU HỦY BẾP ***" : "=== PHIẾU BẾP ===";
        sb.append(header).append("\n");
        sb.append("Khu: ").append(station).append("\n");
        sb.append("Giờ in: ").append(LocalDateTime.now().format(TIME_FMT)).append("\n");

        if (!items.isEmpty()) {
            OrderDetail first = items.get(0);
            var session = first.getOrder().getTableSession();
            if (!session.isTakeaway()) sb.append("Bàn: ");
            sb.append(session.getDisplayName()).append("\n");
            sb.append("Order #").append(orderId).append("\n");
        }

        sb.append("---\n");
        for (OrderDetail d : items) {
            sb.append(d.getDish().getName())
              .append(" x").append(d.getQuantity());
            if (Boolean.TRUE.equals(d.getDish().getSoldByWeight()) && d.getWeightKg() != null) {
                sb.append(" (").append(d.getWeightKg()).append(" kg)");
            }
            sb.append("\n");

            // Tùy chọn
            List<OrderDetailOption> opts = orderDetailOptionRepository.findByOrderDetailId(d.getId());
            if (!opts.isEmpty()) {
                sb.append("  + ");
                sb.append(opts.stream()
                        .map(o -> o.getOptionItem().getName())
                        .collect(Collectors.joining(", ")));
                sb.append("\n");
            }

            if (d.getNote() != null && !d.getNote().isBlank()) {
                sb.append("  Ghi chú: ").append(d.getNote()).append("\n");
            }

            if (isCancel && d.getCancelReason() != null) {
                sb.append("  Lý do hủy: ").append(d.getCancelReason()).append("\n");
            }
        }
        sb.append("==================\n");
        return sb;
    }
}
