package com.group.restaurant.controllers;

import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.report.*;
import com.group.restaurant.services.ReportExportService;
import com.group.restaurant.services.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.OK;

/**
 * FR-RPT-01..05 — Báo cáo doanh thu & Xuất báo cáo (.xlsx / .csv)
 *
 * Tất cả endpoint yêu cầu đăng nhập (configured in SecurityConfig).
 * FR-RPT-01/02/03/05: QL chi nhánh + Quản trị nhà hàng
 * FR-RPT-04: chỉ Quản trị nhà hàng (kiểm tra ở service layer)
 */
@RestController
@RequestMapping("${api.prefix}/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    /**
     * FR-RPT-01: Doanh thu ngày/tháng/năm.
     *
     * Params:
     *   branchIds  — danh sách ID chi nhánh (VD: 1,2,3). Chọn nhiều → cộng dồn.
     *   from       — từ ngày giờ (ISO: 2026-09-01T00:00:00)
     *   to         — đến ngày giờ (ISO: 2026-09-30T23:59:59)
     *   groupBy    — DAY (mặc định) hoặc MONTH — nhóm biểu đồ xu hướng
     *
     * Example:
     *   GET /api/v1/reports/revenue?branchIds=1,2&from=2026-09-01T00:00:00&to=2026-09-30T23:59:59
     */
    @GetMapping("/revenue")
    public ResponseEntity<ResponseSuccess<RevenueReportResponse>> getRevenueReport(
            @RequestParam List<Long> branchIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "DAY") String groupBy) {

        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy báo cáo doanh thu thành công",
                reportService.getRevenueReport(branchIds, from, to, groupBy)));
    }

    /**
     * FR-RPT-02: Doanh thu theo khung giờ trong ngày.
     *
     * Trả về phân bổ theo 24 khung giờ + theo 7 ngày trong tuần.
     *
     * Example:
     *   GET /api/v1/reports/hourly?branchIds=1&from=2026-09-01T00:00:00&to=2026-09-30T23:59:59
     */
    @GetMapping("/hourly")
    public ResponseEntity<ResponseSuccess<HourlyRevenueResponse>> getHourlyRevenue(
            @RequestParam List<Long> branchIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy báo cáo doanh thu theo khung giờ thành công",
                reportService.getHourlyRevenue(branchIds, from, to)));
    }

    /**
     * FR-RPT-03: Món bán chạy / bán chậm.
     *
     * Trả về top N món theo số lượng bán, theo doanh thu và N món bán chậm nhất.
     *
     * Example:
     *   GET /api/v1/reports/top-dishes?branchIds=1&from=2026-09-01T00:00:00&to=2026-09-30T23:59:59&topN=10
     */
    @GetMapping("/top-dishes")
    public ResponseEntity<ResponseSuccess<TopDishResponse>> getTopDishes(
            @RequestParam List<Long> branchIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "10") int topN) {

        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy báo cáo món bán chạy/bán chậm thành công",
                reportService.getTopDishes(branchIds, from, to, topN)));
    }

    /**
     * FR-RPT-04: So sánh doanh thu chi nhánh.
     *
     * Chỉ Quản trị nhà hàng xem được — truyền restaurantId để lấy tất cả chi nhánh.
     * Tự động phát hiện chi nhánh có chỉ số lệch bất thường >30% so với trung bình.
     *
     * Example:
     *   GET /api/v1/reports/branch-comparison?restaurantId=1&from=2026-09-01T00:00:00&to=2026-09-30T23:59:59
     */
    @GetMapping("/branch-comparison")
    public ResponseEntity<ResponseSuccess<BranchRevenueCompareResponse>> getBranchComparison(
            @RequestParam Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy báo cáo so sánh chi nhánh thành công",
                reportService.getBranchComparison(restaurantId, from, to)));
    }

    /**
     * FR-RPT-05: Xuất báo cáo (.xlsx / .csv)
     *
     * Ghi trực tiếp ra HttpServletResponse.getOutputStream() theo chunking.
     * Tránh dispatch ASYNC của Spring MVC gây mất SecurityContext và AccessDeniedException.
     *
     * Params:
     *   type         — REVENUE (mặc định), HOURLY, BRANCH_COMPARISON
     *   format       — xlsx (mặc định) hoặc csv
     *   branchIds    — danh sách ID chi nhánh (cho REVENUE và HOURLY)
     *   restaurantId — ID nhà hàng (cho BRANCH_COMPARISON)
     *   from         — từ ngày giờ (ISO: 2026-09-01T00:00:00)
     *   to           — đến ngày giờ (ISO: 2026-09-30T23:59:59)
     *   groupBy      — DAY (mặc định) hoặc MONTH (cho REVENUE)
     */
    @GetMapping("/export")
    public void exportReport(
            @RequestParam(defaultValue = "REVENUE") String type,
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) List<Long> branchIds,
            @RequestParam(required = false) Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "DAY") String groupBy,
            HttpServletResponse response) throws IOException {

        String ext = "csv".equalsIgnoreCase(format) ? "csv" : "xlsx";
        String contentType = "csv".equalsIgnoreCase(format)
                ? "text/csv; charset=UTF-8"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        String dateSuffix = from.toLocalDate().toString();
        String fileBaseName = switch (type.toUpperCase()) {
            case "HOURLY" -> "bao-cao-khung-gio-" + dateSuffix;
            case "BRANCH_COMPARISON" -> "bao-cao-so-sanh-chi-nhanh-" + dateSuffix;
            default -> "bao-cao-doanh-thu-" + dateSuffix;
        };
        String filename = fileBaseName + "." + ext;

        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        try (OutputStream outputStream = response.getOutputStream()) {
            switch (type.toUpperCase()) {
                case "HOURLY" -> reportExportService.exportHourlyReport(branchIds, from, to, ext, outputStream);
                case "BRANCH_COMPARISON" -> reportExportService.exportBranchComparisonReport(restaurantId, from, to, ext, outputStream);
                default -> reportExportService.exportRevenueReport(branchIds, from, to, groupBy, ext, outputStream);
            }
            outputStream.flush();
        }
    }
}
