package com.group.restaurant.dtos.response.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * So sánh doanh thu chi nhánh
 */
@Getter
@Builder
public class BranchRevenueCompareResponse {

    private List<BranchStat> branches;

    // Trung bình toàn hệ thống (dùng để phát hiện chi nhánh bất thường)
    private BigDecimal avgRevenue;
    private BigDecimal avgInvoiceValue;
    private Double avgCancelRate;

    @Getter
    @Builder
    public static class BranchStat {
        private Long branchId;
        private String branchName;
        private int rank;                       // Thứ hạng theo doanh thu

        private BigDecimal totalRevenue;
        private Long totalInvoices;
        private BigDecimal avgInvoiceValue;     // Giá trị TB / hóa đơn

        // Tỷ lệ hủy món — tách 2 loại theo BR-24
        private Long cancelByRestaurantCount;   // Hủy "lỗi nhà hàng"
        private Long cancelByCustomerCount;     // Hủy "khách đổi ý"
        private Double totalCancelRate;         // % tổng dòng món bị hủy

        // true = chi nhánh này có chỉ số lệch bất thường so với trung bình
        private boolean hasAnomaly;
        private String anomalyNote;             // Mô tả điểm bất thường (nếu có)
    }
}
