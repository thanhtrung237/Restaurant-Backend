package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.response.report.*;
import com.group.restaurant.enums.CancelReason;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.models.Branch;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.InvoiceRepository;
import com.group.restaurant.repositories.OrderDetailRepository;
import com.group.restaurant.services.ReportService;
import com.group.restaurant.utils.SecurityUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    // Trạng thái HĐ tính vào doanh thu: PAID(1) + ISSUED(4)
    private static final List<InvoiceStatus> PAID_STATUSES =
            List.of(InvoiceStatus.PAID, InvoiceStatus.ISSUED);

    // Giá trị int để dùng với native query
    private static final List<Integer> PAID_STATUS_INTS = List.of(1, 4);

    // Ngưỡng phát hiện bất thường: lệch hơn 30% so với trung bình
    private static final double ANOMALY_THRESHOLD = 0.30;

    private final InvoiceRepository invoiceRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final BranchRepository branchRepository;
    private final SecurityUtils securityUtils;

    // ═══════════════════════════════════════════════════════════════════════
    // FR-RPT-01: Doanh thu ngày/tháng/năm
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public RevenueReportResponse getRevenueReport(
            List<Long> branchIds, LocalDateTime from, LocalDateTime to, String groupBy) {

        validateBranchIds(branchIds);
        validateGroupBy(groupBy);

        // ── Tổng quan ──────────────────────────────────────────────────────
        BigDecimal totalRevenue = invoiceRepository.sumRevenue(branchIds, PAID_STATUSES, from, to);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        BigDecimal totalGrossRevenue =
                invoiceRepository.sumGrossRevenue(branchIds, PAID_STATUSES, from, to);
        if (totalGrossRevenue == null) totalGrossRevenue = totalRevenue;

        BigDecimal totalDiscount =
                invoiceRepository.sumDiscount(branchIds, PAID_STATUSES, from, to);
        if (totalDiscount == null) totalDiscount = BigDecimal.ZERO;

        long totalInvoices = invoiceRepository.countInvoices(branchIds, PAID_STATUSES, from, to);
        long totalGuests = invoiceRepository.sumGuests(branchIds, PAID_STATUSES, from, to);

        BigDecimal avgPerInvoice =
                totalInvoices > 0
                        ? totalRevenue.divide(
                                BigDecimal.valueOf(totalInvoices), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        // ── Doanh thu theo phương thức thanh toán ──────────────────────────
        List<Object[]> methodRows =
                invoiceRepository.revenueByPaymentMethod(branchIds, PAID_STATUSES, from, to);
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        for (Object[] row : methodRows) {
            String method = row[0] != null ? row[0].toString() : "UNKNOWN";
            BigDecimal amount =
                    row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            byMethod.put(method, amount);
        }

        // ── So sánh kỳ liền trước ──────────────────────────────────────────
        // Fix: dùng Duration.between() trực tiếp, không nhân thêm 1_000_000
        java.time.Duration period = java.time.Duration.between(from, to);
        LocalDateTime prevFrom = from.minus(period);
        LocalDateTime prevTo = from.minusNanos(1);
        BigDecimal prevRevenue =
                invoiceRepository.sumRevenue(branchIds, PAID_STATUSES, prevFrom, prevTo);
        if (prevRevenue == null) prevRevenue = BigDecimal.ZERO;

        Double growthPct = null;
        if (prevRevenue.compareTo(BigDecimal.ZERO) > 0) {
            growthPct =
                    totalRevenue
                            .subtract(prevRevenue)
                            .divide(prevRevenue, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue();
        }

        // ── Xu hướng ───────────────────────────────────────────────────────
        List<RevenueReportResponse.RevenueTrendPoint> trend = new ArrayList<>();
        boolean byMonth = "MONTH".equalsIgnoreCase(groupBy);
        List<Object[]> trendRows =
                byMonth
                        ? invoiceRepository.revenueTrendByMonth(
                                branchIds, PAID_STATUS_INTS, from, to)
                        : invoiceRepository.revenueTrendByDay(
                                branchIds, PAID_STATUS_INTS, from, to);

        for (Object[] row : trendRows) {
            trend.add(
                    RevenueReportResponse.RevenueTrendPoint.builder()
                            .label(row[0] != null ? row[0].toString() : "")
                            .revenue(
                                    row[1] != null
                                            ? new BigDecimal(row[1].toString())
                                            : BigDecimal.ZERO)
                            .invoiceCount(row[2] != null ? ((Number) row[2]).longValue() : 0L)
                            .build());
        }

        return RevenueReportResponse.builder()
                .totalRevenue(totalRevenue)
                .totalGrossRevenue(totalGrossRevenue)
                .totalDiscount(totalDiscount)
                .totalInvoices(totalInvoices)
                .totalGuests(totalGuests)
                .avgRevenuePerInvoice(avgPerInvoice)
                .revenueByPaymentMethod(byMethod)
                .previousPeriodRevenue(prevRevenue)
                .revenueGrowthPercent(growthPct)
                .trend(trend)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FR-RPT-02: Doanh thu theo khung giờ
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public HourlyRevenueResponse getHourlyRevenue(
            List<Long> branchIds, LocalDateTime from, LocalDateTime to) {

        validateBranchIds(branchIds);

        // Theo giờ (0-23)
        List<Object[]> hourRows =
                invoiceRepository.revenueByHour(branchIds, PAID_STATUS_INTS, from, to);
        Map<Integer, Object[]> hourMap = new HashMap<>();
        for (Object[] row : hourRows) {
            int hour = ((Number) row[0]).intValue();
            hourMap.put(hour, row);
        }

        List<HourlyRevenueResponse.HourSlot> hourSlots = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Object[] row = hourMap.get(h);
            hourSlots.add(
                    HourlyRevenueResponse.HourSlot.builder()
                            .hour(h)
                            .label(String.format("%02d:00-%02d:00", h, (h + 1) % 24))
                            .revenue(
                                    row != null
                                            ? new BigDecimal(row[1].toString())
                                            : BigDecimal.ZERO)
                            .tableCount(row != null ? ((Number) row[2]).longValue() : 0L)
                            .build());
        }

        // Theo ngày trong tuần
        // DAYOFWEEK MySQL: 1=CN, 2=T2 ... 7=T7 — chuyển sang 1=T2 ... 7=CN cho đặc tả
        List<Object[]> dowRows =
                invoiceRepository.revenueByDayOfWeek(branchIds, PAID_STATUS_INTS, from, to);
        Map<Integer, Object[]> dowMap = new HashMap<>();
        for (Object[] row : dowRows) {
            int mysqlDow = ((Number) row[0]).intValue();
            // MySQL: 1=Sun→7, 2=Mon→1... chuyển sang 1=Mon..7=Sun
            int iso = mysqlDow == 1 ? 7 : mysqlDow - 1;
            dowMap.put(iso, row);
        }

        String[] dayLabels = {"", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};
        List<HourlyRevenueResponse.DayOfWeekSlot> dowSlots = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            Object[] row = dowMap.get(d);
            dowSlots.add(
                    HourlyRevenueResponse.DayOfWeekSlot.builder()
                            .dayOfWeek(d)
                            .label(dayLabels[d])
                            .revenue(
                                    row != null
                                            ? new BigDecimal(row[1].toString())
                                            : BigDecimal.ZERO)
                            .invoiceCount(row != null ? ((Number) row[2]).longValue() : 0L)
                            .build());
        }

        return HourlyRevenueResponse.builder()
                .hourlySlots(hourSlots)
                .dayOfWeekSlots(dowSlots)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FR-RPT-03: Món bán chạy / bán chậm
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public TopDishResponse getTopDishes(
            List<Long> branchIds, LocalDateTime from, LocalDateTime to, int topN) {

        validateBranchIds(branchIds);
        int limit = topN > 0 ? topN : 10;

        List<Object[]> rows = orderDetailRepository.dishSalesSummary(branchIds, from, to);

        // Map toàn bộ thành DishRankItem
        List<TopDishResponse.DishRankItem> allItems = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            allItems.add(
                    TopDishResponse.DishRankItem.builder()
                            .dishId(((Number) row[0]).longValue())
                            .dishName(row[1] != null ? row[1].toString() : "")
                            .categoryName(row[2] != null ? row[2].toString() : "")
                            .totalQuantity(row[3] != null ? ((Number) row[3]).longValue() : 0L)
                            .totalRevenue(
                                    row[4] != null
                                            ? new BigDecimal(row[4].toString())
                                            : BigDecimal.ZERO)
                            .rank(i + 1)
                            .build());
        }

        // Bảng xếp hạng theo doanh thu (đã sort DESC từ query)
        List<TopDishResponse.DishRankItem> topByRevenue =
                allItems.stream().limit(limit).collect(Collectors.toList());

        // Bảng xếp hạng theo số lượng (sort lại)
        List<TopDishResponse.DishRankItem> byQty =
                allItems.stream()
                        .sorted(
                                Comparator.comparingLong(
                                                TopDishResponse.DishRankItem::getTotalQuantity)
                                        .reversed())
                        .limit(limit)
                        .collect(Collectors.toList());
        // Reset rank
        List<TopDishResponse.DishRankItem> topByQuantity = new ArrayList<>();
        for (int i = 0; i < byQty.size(); i++) {
            TopDishResponse.DishRankItem item = byQty.get(i);
            topByQuantity.add(
                    TopDishResponse.DishRankItem.builder()
                            .dishId(item.getDishId())
                            .dishName(item.getDishName())
                            .categoryName(item.getCategoryName())
                            .totalQuantity(item.getTotalQuantity())
                            .totalRevenue(item.getTotalRevenue())
                            .rank(i + 1)
                            .build());
        }

        // Danh sách bán chậm nhất (đảo ngược từ cuối)
        int slowSize = Math.min(limit, allItems.size());
        List<TopDishResponse.DishRankItem> rawSlowest =
                allItems.subList(Math.max(0, allItems.size() - slowSize), allItems.size());
        List<TopDishResponse.DishRankItem> slowestDishes = new ArrayList<>();
        for (int i = 0; i < rawSlowest.size(); i++) {
            TopDishResponse.DishRankItem item = rawSlowest.get(i);
            slowestDishes.add(
                    TopDishResponse.DishRankItem.builder()
                            .dishId(item.getDishId())
                            .dishName(item.getDishName())
                            .categoryName(item.getCategoryName())
                            .totalQuantity(item.getTotalQuantity())
                            .totalRevenue(item.getTotalRevenue())
                            .rank(i + 1)
                            .build());
        }

        return TopDishResponse.builder()
                .topByQuantity(topByQuantity)
                .topByRevenue(topByRevenue)
                .slowestDishes(slowestDishes)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FR-RPT-04: So sánh doanh thu chi nhánh
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public BranchRevenueCompareResponse getBranchComparison(
            Long restaurantId, LocalDateTime from, LocalDateTime to) {

        // FR-RPT-04: Chỉ Quản trị nhà hàng xem được — verify restaurantId thuộc nhà hàng đang
        // request
        // SecurityUtils lấy restaurantId từ JWT của user hiện tại để đảm bảo cách ly tenant (BR-21)
        Long currentRestaurantId = securityUtils.getCurrentRestaurantId();
        if (currentRestaurantId == null || !currentRestaurantId.equals(restaurantId)) {
            throw new ConflictException("Không có quyền xem báo cáo của nhà hàng này (BR-21)");
        }

        // Lấy tất cả chi nhánh của nhà hàng
        List<Branch> branches = branchRepository.findByRestaurantId(restaurantId);
        if (branches.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Không tìm thấy chi nhánh nào cho nhà hàng ID: " + restaurantId);
        }

        List<Long> branchIds = branches.stream().map(Branch::getId).collect(Collectors.toList());
        Map<Long, String> branchNames =
                branches.stream().collect(Collectors.toMap(Branch::getId, Branch::getName));

        // ── Doanh thu theo chi nhánh ───────────────────────────────────────
        List<Object[]> revRows =
                invoiceRepository.revenueGroupedByBranch(branchIds, PAID_STATUSES, from, to);
        Map<Long, BigDecimal> revenueMap = new HashMap<>();
        Map<Long, Long> invoiceMap = new HashMap<>();
        for (Object[] row : revRows) {
            long bId = ((Number) row[0]).longValue();
            revenueMap.put(
                    bId, row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO);
            invoiceMap.put(bId, row[2] != null ? ((Number) row[2]).longValue() : 0L);
        }

        // ── Tỷ lệ hủy theo chi nhánh + lý do ─────────────────────────────
        List<Object[]> cancelRows =
                orderDetailRepository.cancelCountByBranchAndReason(branchIds, from, to);
        Map<Long, Long> cancelRestaurant = new HashMap<>(); // lỗi nhà hàng
        Map<Long, Long> cancelCustomer = new HashMap<>(); // khách đổi ý
        for (Object[] row : cancelRows) {
            long bId = ((Number) row[0]).longValue();
            int reason = row[1] != null ? ((Number) row[1]).intValue() : -1;
            long cnt = ((Number) row[2]).longValue();
            if (reason == CancelReason.RESTAURANT_ERROR.getValue()) {
                cancelRestaurant.merge(bId, cnt, Long::sum);
            } else if (reason == CancelReason.CUSTOMER_CHANGED_MIND.getValue()) {
                cancelCustomer.merge(bId, cnt, Long::sum);
            }
        }

        List<Object[]> totalDetailRows =
                orderDetailRepository.totalDetailCountByBranch(branchIds, from, to);
        Map<Long, Long> totalDetailMap = new HashMap<>();
        for (Object[] row : totalDetailRows) {
            totalDetailMap.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        // ── Tính trung bình để phát hiện bất thường ───────────────────────
        long branchCount = branches.size();
        BigDecimal sumAll = revenueMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgRevenue =
                branchCount > 0
                        ? sumAll.divide(BigDecimal.valueOf(branchCount), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        // Tổng invoices và avg invoice value trung bình
        long sumInvoices = invoiceMap.values().stream().mapToLong(Long::longValue).sum();
        BigDecimal sumAvgInvoiceValue = BigDecimal.ZERO;
        int validBranches = 0;
        for (Long bId : branchIds) {
            long inv = invoiceMap.getOrDefault(bId, 0L);
            BigDecimal rev = revenueMap.getOrDefault(bId, BigDecimal.ZERO);
            if (inv > 0) {
                sumAvgInvoiceValue =
                        sumAvgInvoiceValue.add(
                                rev.divide(BigDecimal.valueOf(inv), 2, RoundingMode.HALF_UP));
                validBranches++;
            }
        }
        BigDecimal avgInvoiceValue =
                validBranches > 0
                        ? sumAvgInvoiceValue.divide(
                                BigDecimal.valueOf(validBranches), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        long sumCancelAll = 0;
        long sumDetailAll = 0;
        for (Long bId : branchIds) {
            sumCancelAll +=
                    cancelRestaurant.getOrDefault(bId, 0L) + cancelCustomer.getOrDefault(bId, 0L);
            sumDetailAll += totalDetailMap.getOrDefault(bId, 0L);
        }
        Double avgCancelRate = sumDetailAll > 0 ? (double) sumCancelAll / sumDetailAll * 100 : 0.0;

        // ── Build từng chi nhánh, sort theo doanh thu giảm dần ────────────
        List<BranchRevenueCompareResponse.BranchStat> stats =
                branchIds.stream()
                        .map(
                                bId -> {
                                    BigDecimal rev = revenueMap.getOrDefault(bId, BigDecimal.ZERO);
                                    long inv = invoiceMap.getOrDefault(bId, 0L);
                                    BigDecimal avg =
                                            inv > 0
                                                    ? rev.divide(
                                                            BigDecimal.valueOf(inv),
                                                            2,
                                                            RoundingMode.HALF_UP)
                                                    : BigDecimal.ZERO;

                                    long cRest = cancelRestaurant.getOrDefault(bId, 0L);
                                    long cCust = cancelCustomer.getOrDefault(bId, 0L);
                                    long total = totalDetailMap.getOrDefault(bId, 0L);
                                    double cancelRate =
                                            total > 0
                                                    ? (double) (cRest + cCust) / total * 100
                                                    : 0.0;

                                    // Phát hiện bất thường: doanh thu hoặc tỷ lệ hủy lệch > 30% so
                                    // với trung bình
                                    boolean hasAnomaly = false;
                                    String anomalyNote = null;
                                    if (avgRevenue.compareTo(BigDecimal.ZERO) > 0) {
                                        double diff =
                                                rev.subtract(avgRevenue)
                                                        .abs()
                                                        .divide(avgRevenue, 4, RoundingMode.HALF_UP)
                                                        .doubleValue();
                                        if (diff > ANOMALY_THRESHOLD) {
                                            hasAnomaly = true;
                                            anomalyNote =
                                                    rev.compareTo(avgRevenue) < 0
                                                            ? "Doanh thu thấp hơn trung bình hệ thống >30%"
                                                            : "Doanh thu cao hơn trung bình hệ thống >30%";
                                        }
                                    }
                                    if (avgCancelRate != null
                                            && avgCancelRate > 0
                                            && cancelRate
                                                    > avgCancelRate * (1 + ANOMALY_THRESHOLD)) {
                                        hasAnomaly = true;
                                        anomalyNote =
                                                (anomalyNote != null ? anomalyNote + "; " : "")
                                                        + "Tỷ lệ hủy món cao bất thường";
                                    }

                                    return BranchRevenueCompareResponse.BranchStat.builder()
                                            .branchId(bId)
                                            .branchName(branchNames.getOrDefault(bId, ""))
                                            .rank(0) // tạm thời, set lại sau khi sort
                                            .totalRevenue(rev)
                                            .totalInvoices(inv)
                                            .avgInvoiceValue(avg)
                                            .cancelByRestaurantCount(cRest)
                                            .cancelByCustomerCount(cCust)
                                            .totalCancelRate(cancelRate)
                                            .hasAnomaly(hasAnomaly)
                                            .anomalyNote(anomalyNote)
                                            .build();
                                })
                        .sorted(
                                Comparator.comparing(
                                                BranchRevenueCompareResponse.BranchStat
                                                        ::getTotalRevenue)
                                        .reversed())
                        .collect(Collectors.toList());

        // Gán rank sau khi sort
        List<BranchRevenueCompareResponse.BranchStat> ranked = new ArrayList<>();
        for (int i = 0; i < stats.size(); i++) {
            BranchRevenueCompareResponse.BranchStat s = stats.get(i);
            ranked.add(
                    BranchRevenueCompareResponse.BranchStat.builder()
                            .branchId(s.getBranchId())
                            .branchName(s.getBranchName())
                            .rank(i + 1)
                            .totalRevenue(s.getTotalRevenue())
                            .totalInvoices(s.getTotalInvoices())
                            .avgInvoiceValue(s.getAvgInvoiceValue())
                            .cancelByRestaurantCount(s.getCancelByRestaurantCount())
                            .cancelByCustomerCount(s.getCancelByCustomerCount())
                            .totalCancelRate(s.getTotalCancelRate())
                            .hasAnomaly(s.isHasAnomaly())
                            .anomalyNote(s.getAnomalyNote())
                            .build());
        }

        return BranchRevenueCompareResponse.builder()
                .branches(ranked)
                .avgRevenue(avgRevenue)
                .avgInvoiceValue(avgInvoiceValue)
                .avgCancelRate(avgCancelRate)
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void validateBranchIds(List<Long> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            throw new IllegalArgumentException("Danh sách chi nhánh không được để trống");
        }
    }

    /**
     * Validate groupBy chỉ chấp nhận "DAY" hoặc "MONTH".
     * Giá trị khác → trả 400 thay vì silently fallback sang DAY.
     */
    private void validateGroupBy(String groupBy) {
        if (groupBy != null
                && !groupBy.isBlank()
                && !"DAY".equalsIgnoreCase(groupBy)
                && !"MONTH".equalsIgnoreCase(groupBy)) {
            throw new InvalidParamException(
                    "groupBy chỉ chấp nhận 'DAY' hoặc 'MONTH', nhận được: '" + groupBy + "'");
        }
    }
}
