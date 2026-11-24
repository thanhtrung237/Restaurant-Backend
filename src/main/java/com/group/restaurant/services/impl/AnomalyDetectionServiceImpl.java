package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.response.report.AnomalyAlert;
import com.group.restaurant.dtos.response.report.AnomalyReportResponse;
import com.group.restaurant.enums.AnomalySeverity;
import com.group.restaurant.enums.AnomalyType;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Invoice;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.InvoiceRepository;
import com.group.restaurant.repositories.OrderDetailRepository;
import com.group.restaurant.services.AnomalyDetectionService;
import com.group.restaurant.services.GeminiAiService;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    private final OrderDetailRepository orderDetailRepository;
    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;
    private final GeminiAiService geminiAiService;

    private static final List<InvoiceStatus> PAID_STATUSES =
            List.of(InvoiceStatus.PAID, InvoiceStatus.ISSUED);
    private final Locale vnLocale = new Locale("vi", "VN");
    private final NumberFormat currencyFormatter =
            NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    @Override
    @Transactional(readOnly = true)
    public AnomalyReportResponse detectAnomalies(
            List<Long> branchIds, LocalDateTime from, LocalDateTime to) {
        if (branchIds == null || branchIds.isEmpty()) {
            throw new IllegalArgumentException("Danh sách chi nhánh không được để trống");
        }

        // Cache map branch id -> name
        Map<Long, String> branchNameMap = new HashMap<>();
        List<Branch> branches = branchRepository.findAllById(branchIds);
        for (Branch b : branches) {
            branchNameMap.put(b.getId(), b.getName());
        }

        List<AnomalyAlert> alerts = new ArrayList<>();

        // ═══════════════════════════════════════════════════════════════════
        // RULE 1: Đột biến hủy món theo khung giờ (Surge Cancellation in Hour)
        // ═══════════════════════════════════════════════════════════════════
        try {
            List<Object[]> hourlySpikes =
                    orderDetailRepository.findHourlyCancellationSpikes(branchIds, from, to);
            for (Object[] row : hourlySpikes) {
                Long bId = ((Number) row[0]).longValue();
                int hr = ((Number) row[1]).intValue();
                long count = ((Number) row[2]).longValue();

                String bName = branchNameMap.getOrDefault(bId, "Chi nhánh " + bId);
                String shiftName =
                        (hr >= 6 && hr < 14)
                                ? "Ca sáng"
                                : (hr >= 14 && hr < 17) ? "Ca chiều" : "Ca tối";
                String location =
                        String.format("%s · %02dh00 - %02dh59 (%s)", bName, hr, hr, shiftName);

                AnomalySeverity severity =
                        count >= 4 ? AnomalySeverity.CRITICAL : AnomalySeverity.WARNING;
                double score = Math.min(0.95, 0.60 + (count * 0.08));

                Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("branchId", bId);
                metrics.put("hour", hr);
                metrics.put("cancelledCount", count);
                metrics.put("timeFrame", String.format("%02d:00 - %02d:59", hr, hr));

                AnomalyAlert alert =
                        AnomalyAlert.builder()
                                .id("ANOM-SURGE-" + bId + "-" + hr)
                                .type(AnomalyType.SURGE_CANCELLATION)
                                .severity(severity)
                                .title("Tỷ lệ hủy món tăng đột biến")
                                .location(location)
                                .description(
                                        String.format(
                                                "Ghi nhận %d lượt hủy món trong khung giờ %02dh00 - %02dh59, cao hơn đáng kể so với mức vận hành bình thường.",
                                                count, hr))
                                .detectedAt(LocalDateTime.now())
                                .anomalyScore(score)
                                .metrics(metrics)
                                .build();

                geminiAiService.enrichAnomalyWithAi(alert);
                alerts.add(alert);
            }
        } catch (Exception e) {
            log.warn("Error running rule 1 (hourlySpikes): {}", e.getMessage());
        }

        // ═══════════════════════════════════════════════════════════════════
        // RULE 2: Bàn bị hủy món nhiều lần (Repeated Table Cancellations)
        // ═══════════════════════════════════════════════════════════════════
        try {
            List<Object[]> sessionCancellations =
                    orderDetailRepository.findSessionsWithMultipleCancellations(
                            branchIds, from, to);
            for (Object[] row : sessionCancellations) {
                Long sId = ((Number) row[0]).longValue();
                Long bId = ((Number) row[1]).longValue();
                String tableName = row[2] != null ? row[2].toString() : "Bàn #" + sId;
                long count = ((Number) row[3]).longValue();
                BigDecimal amount =
                        row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;

                String bName = branchNameMap.getOrDefault(bId, "Chi nhánh " + bId);
                String location = String.format("%s · %s", bName, tableName);

                AnomalySeverity severity =
                        (count >= 3 || amount.compareTo(new BigDecimal("200000")) > 0)
                                ? AnomalySeverity.CRITICAL
                                : AnomalySeverity.WARNING;

                double score = Math.min(0.92, 0.65 + (count * 0.07));

                Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("sessionId", sId);
                metrics.put("branchId", bId);
                metrics.put("tableName", tableName);
                metrics.put("cancelledCount", count);
                metrics.put("cancelledAmount", amount);

                AnomalyAlert alert =
                        AnomalyAlert.builder()
                                .id("ANOM-TABLE-" + sId)
                                .type(AnomalyType.REPEATED_TABLE_CANCELLATIONS)
                                .severity(severity)
                                .title("Bàn phát sinh hủy món nhiều lần")
                                .location(location)
                                .description(
                                        String.format(
                                                "Phiên %s ghi nhận %d món bị hủy với tổng giá trị hủy ước tính khoảng %s.",
                                                tableName, count, formatVnd(amount)))
                                .detectedAt(LocalDateTime.now())
                                .anomalyScore(score)
                                .metrics(metrics)
                                .build();

                geminiAiService.enrichAnomalyWithAi(alert);
                alerts.add(alert);
            }
        } catch (Exception e) {
            log.warn("Error running rule 2 (sessionCancellations): {}", e.getMessage());
        }

        // ═══════════════════════════════════════════════════════════════════
        // RULE 3: Chiết khấu thủ công vượt hạn mức (Suspicious Manual Discount)
        // ═══════════════════════════════════════════════════════════════════
        try {
            List<Invoice> manualDiscountInvoices =
                    invoiceRepository.findInvoicesWithManualDiscount(
                            branchIds, PAID_STATUSES, from, to);
            int countDiscountAlerts = 0;
            for (Invoice inv : manualDiscountInvoices) {
                if (countDiscountAlerts >= 3) break; // Lấy tối đa 3 ca nổi bật

                BigDecimal manualDiscount = inv.getTotalManualDiscount();
                BigDecimal grandTotal = inv.getGrandTotal();
                BigDecimal subTotal =
                        inv.getSubTotalBeforeDiscount() != null
                                ? inv.getSubTotalBeforeDiscount()
                                : grandTotal;

                // Tỷ lệ giảm giá trên tổng đơn
                double discountPercent =
                        subTotal.compareTo(BigDecimal.ZERO) > 0
                                ? manualDiscount
                                                .divide(subTotal, 4, java.math.RoundingMode.HALF_UP)
                                                .doubleValue()
                                        * 100
                                : 0.0;

                if (discountPercent >= 15.0
                        || manualDiscount.compareTo(new BigDecimal("100000")) >= 0) {
                    countDiscountAlerts++;
                    Long bId = inv.getBranch().getId();
                    String bName = branchNameMap.getOrDefault(bId, "Chi nhánh " + bId);
                    String location =
                            String.format("%s · Hóa đơn %s", bName, inv.getInvoiceNumber());

                    AnomalySeverity severity =
                            discountPercent >= 30.0
                                    ? AnomalySeverity.CRITICAL
                                    : AnomalySeverity.WARNING;
                    double score = Math.min(0.90, 0.50 + (discountPercent / 100.0));

                    Map<String, Object> metrics = new LinkedHashMap<>();
                    metrics.put("invoiceId", inv.getId());
                    metrics.put("invoiceNumber", inv.getInvoiceNumber());
                    metrics.put("manualDiscountAmount", manualDiscount);
                    metrics.put("discountPercent", Math.round(discountPercent * 10.0) / 10.0);
                    metrics.put("grandTotal", grandTotal);

                    AnomalyAlert alert =
                            AnomalyAlert.builder()
                                    .id("ANOM-DISC-" + inv.getId())
                                    .type(AnomalyType.SUSPICIOUS_DISCOUNT)
                                    .severity(severity)
                                    .title("Chiết khấu thủ công giá trị cao")
                                    .location(location)
                                    .description(
                                            String.format(
                                                    "Hóa đơn %s được áp dụng giảm giá tay %s (chiếm %.1f%% tổng đơn), cần kiểm tra quyền phê duyệt.",
                                                    inv.getInvoiceNumber(),
                                                    formatVnd(manualDiscount),
                                                    discountPercent))
                                    .detectedAt(
                                            inv.getCreatedAt() != null
                                                    ? inv.getCreatedAt()
                                                    : LocalDateTime.now())
                                    .anomalyScore(score)
                                    .metrics(metrics)
                                    .build();

                    geminiAiService.enrichAnomalyWithAi(alert);
                    alerts.add(alert);
                }
            }
        } catch (Exception e) {
            log.warn("Error running rule 3 (manualDiscountInvoices): {}", e.getMessage());
        }

        // Sắp xếp các cảnh báo theo mức độ nghiêm trọng (CRITICAL trước, sau đó WARNING) và điểm
        // bất thường giảm dần
        alerts.sort(
                (a, b) -> {
                    int sevCompare = b.getSeverity().compareTo(a.getSeverity());
                    if (sevCompare != 0) return sevCompare;
                    return Double.compare(
                            b.getAnomalyScore() != null ? b.getAnomalyScore() : 0.0,
                            a.getAnomalyScore() != null ? a.getAnomalyScore() : 0.0);
                });

        int critical =
                (int)
                        alerts.stream()
                                .filter(a -> a.getSeverity() == AnomalySeverity.CRITICAL)
                                .count();
        int warning =
                (int)
                        alerts.stream()
                                .filter(a -> a.getSeverity() == AnomalySeverity.WARNING)
                                .count();

        // Sinh bản tin điều hành tóm tắt
        String executiveSummary = geminiAiService.generateExecutiveSummary(alerts);

        return AnomalyReportResponse.builder()
                .totalAnomalies(alerts.size())
                .criticalCount(critical)
                .warningCount(warning)
                .alerts(alerts)
                .aiExecutiveSummary(executiveSummary)
                .evaluatedAt(LocalDateTime.now())
                .build();
    }

    private String formatVnd(BigDecimal amount) {
        if (amount == null) return "0 đ";
        return String.format("%,d đ", amount.longValue()).replace(',', '.');
    }
}
