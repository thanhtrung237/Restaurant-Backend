package com.group.restaurant.services;

import com.group.restaurant.dtos.response.report.BranchRevenueCompareResponse;
import com.group.restaurant.dtos.response.report.HourlyRevenueResponse;
import com.group.restaurant.dtos.response.report.RevenueReportResponse;
import com.group.restaurant.dtos.response.report.TopDishResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FR-RPT-01..04 — Báo cáo doanh thu
 */
public interface ReportService {

    /**
     * FR-RPT-01: Doanh thu ngày/tháng/năm.
     *
     * @param branchIds  danh sách chi nhánh (chọn nhiều → cộng dồn)
     * @param from       từ ngày giờ
     * @param to         đến ngày giờ
     * @param groupBy    "DAY" hoặc "MONTH" — nhóm xu hướng theo ngày hay tháng
     */
    RevenueReportResponse getRevenueReport(
            List<Long> branchIds, LocalDateTime from, LocalDateTime to, String groupBy);

    /**
     * FR-RPT-02: Doanh thu theo khung giờ trong ngày.
     *
     * @param branchIds danh sách chi nhánh
     * @param from      từ ngày giờ
     * @param to        đến ngày giờ
     */
    HourlyRevenueResponse getHourlyRevenue(
            List<Long> branchIds, LocalDateTime from, LocalDateTime to);

    /**
     * FR-RPT-03: Món bán chạy / bán chậm.
     *
     * @param branchIds danh sách chi nhánh
     * @param from      từ ngày giờ
     * @param to        đến ngày giờ
     * @param topN      số lượng món trong bảng xếp hạng (mặc định 10)
     */
    TopDishResponse getTopDishes(
            List<Long> branchIds, LocalDateTime from, LocalDateTime to, int topN);

    /**
     * FR-RPT-04: So sánh doanh thu chi nhánh.
     * Chỉ Quản trị nhà hàng gọi.
     *
     * @param restaurantId nhà hàng cần so sánh
     * @param from         từ ngày giờ
     * @param to           đến ngày giờ
     */
    BranchRevenueCompareResponse getBranchComparison(
            Long restaurantId, LocalDateTime from, LocalDateTime to);
}
