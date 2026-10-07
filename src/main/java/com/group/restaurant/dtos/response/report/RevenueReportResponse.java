package com.group.restaurant.dtos.response.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 *  Doanh thu ngày/tháng/năm
 */
@Getter
@Builder
public class RevenueReportResponse {

    // ── Tổng quan ───────────────────────────────────────────────────────
    private BigDecimal totalRevenue;         // Tổng doanh thu
    private Long totalInvoices;              // Số hóa đơn
    private Long totalGuests;                // Số khách (từ guest_count)
    private BigDecimal avgRevenuePerInvoice; // Giá trị TB / hóa đơn

    // ── Chi tiết theo phương thức thanh toán ────────────────────────────
    // key = tên method (CASH, BANK_TRANSFER...), value = tổng tiền
    private Map<String, BigDecimal> revenueByPaymentMethod;

    // ── So sánh kỳ liền trước ───────────────────────────────────────────
    private BigDecimal previousPeriodRevenue;  // Doanh thu kỳ trước
    private Double revenueGrowthPercent;       // Tăng trưởng % (null nếu kỳ trước = 0)

    // ── Xu hướng theo kỳ (biểu đồ) ─────────────────────────────────────
    // key = label ngày/tháng (VD "2026-09-21"), value = doanh thu
    private List<RevenueTrendPoint> trend;

    @Getter
    @Builder
    public static class RevenueTrendPoint {
        private String label;        // "2026-09-21" hoặc "2026-09"
        private BigDecimal revenue;
        private Long invoiceCount;
    }
}
