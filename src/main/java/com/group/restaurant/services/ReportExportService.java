package com.group.restaurant.services;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FR-RPT-05: Xuất báo cáo (.xlsx/.csv)
 * Hỗ trợ xuất dữ liệu dạng chunking / streaming để tránh tràn bộ nhớ khi dữ liệu lớn.
 */
public interface ReportExportService {

    /**
     * Xuất báo cáo doanh thu tổng quan và chi tiết hóa đơn.
     * Dùng chunking để truy vấn và ghi từng khối hóa đơn.
     *
     * @param branchIds danh sách chi nhánh được chọn
     * @param from từ ngày giờ
     * @param to đến ngày giờ
     * @param groupBy "DAY" hoặc "MONTH"
     * @param format "xlsx" hoặc "csv"
     * @param outputStream luồng đầu ra HTTP (StreamingResponseBody)
     */
    void exportRevenueReport(
            List<Long> branchIds,
            LocalDateTime from,
            LocalDateTime to,
            String groupBy,
            String format,
            OutputStream outputStream);

    /**
     * Xuất báo cáo doanh thu theo khung giờ.
     */
    void exportHourlyReport(
            List<Long> branchIds,
            LocalDateTime from,
            LocalDateTime to,
            String format,
            OutputStream outputStream);

    /**
     * Xuất báo cáo so sánh chi nhánh (dành cho Quản trị nhà hàng).
     */
    void exportBranchComparisonReport(
            Long restaurantId,
            LocalDateTime from,
            LocalDateTime to,
            String format,
            OutputStream outputStream);
}
