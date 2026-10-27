package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.response.report.BranchRevenueCompareResponse;
import com.group.restaurant.dtos.response.report.HourlyRevenueResponse;
import com.group.restaurant.dtos.response.report.RevenueReportResponse;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Invoice;
import com.group.restaurant.models.Payment;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.InvoiceRepository;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.ReportExportService;
import com.group.restaurant.services.ReportService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportExportServiceImpl implements ReportExportService {

    private static final int CHUNK_SIZE = 500;
    private static final int SXSSF_WINDOW_SIZE = 100;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final List<InvoiceStatus> PAID_STATUSES = List.of(InvoiceStatus.PAID, InvoiceStatus.ISSUED);

    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;
    private final ReportService reportService;
    private final AuditLogService auditLogService;
    private final SecurityUtils securityUtils;

    @Override
    public void exportRevenueReport(
            List<Long> branchIds,
            LocalDateTime from,
            LocalDateTime to,
            String groupBy,
            String format,
            OutputStream outputStream) {

        validateBranchIds(branchIds);

        RevenueReportResponse summary = reportService.getRevenueReport(branchIds, from, to, groupBy);
        List<Branch> branches = branchRepository.findAllById(branchIds);
        String branchNames = branches.stream().map(Branch::getName).collect(Collectors.joining(", "));

        // Ghi log nhật ký EXPORT (QĐ-19 / BC-6 / FR-LOG-01)
        recordAuditLog("Báo cáo doanh thu (" + groupBy + ")", format, branchIds.size() == 1 ? branchIds.get(0) : null);

        boolean isCsv = "csv".equalsIgnoreCase(format);
        if (isCsv) {
            exportRevenueReportCsv(branchIds, from, to, groupBy, summary, branchNames, outputStream);
        } else {
            exportRevenueReportExcel(branchIds, from, to, groupBy, summary, branchNames, outputStream);
        }
    }

    @Override
    public void exportHourlyReport(
            List<Long> branchIds,
            LocalDateTime from,
            LocalDateTime to,
            String format,
            OutputStream outputStream) {

        validateBranchIds(branchIds);

        HourlyRevenueResponse hourly = reportService.getHourlyRevenue(branchIds, from, to);
        List<Branch> branches = branchRepository.findAllById(branchIds);
        String branchNames = branches.stream().map(Branch::getName).collect(Collectors.joining(", "));

        recordAuditLog("Báo cáo doanh thu theo khung giờ", format, branchIds.size() == 1 ? branchIds.get(0) : null);

        boolean isCsv = "csv".equalsIgnoreCase(format);
        if (isCsv) {
            exportHourlyReportCsv(from, to, hourly, branchNames, outputStream);
        } else {
            exportHourlyReportExcel(from, to, hourly, branchNames, outputStream);
        }
    }

    @Override
    public void exportBranchComparisonReport(
            Long restaurantId,
            LocalDateTime from,
            LocalDateTime to,
            String format,
            OutputStream outputStream) {

        if (restaurantId == null) {
            throw new IllegalArgumentException("restaurantId không được để trống");
        }

        BranchRevenueCompareResponse comparison = reportService.getBranchComparison(restaurantId, from, to);

        recordAuditLog("Báo cáo so sánh chi nhánh (nhà hàng #" + restaurantId + ")", format, null);

        boolean isCsv = "csv".equalsIgnoreCase(format);
        if (isCsv) {
            exportBranchComparisonCsv(from, to, comparison, restaurantId, outputStream);
        } else {
            exportBranchComparisonExcel(from, to, comparison, restaurantId, outputStream);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. REVENUE REPORT: EXCEL & CSV (Dùng Chunking cho chi tiết hóa đơn)
    // ═══════════════════════════════════════════════════════════════════════

    private void exportRevenueReportExcel(
            List<Long> branchIds,
            LocalDateTime from,
            LocalDateTime to,
            String groupBy,
            RevenueReportResponse summary,
            String branchNames,
            OutputStream outputStream) {

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSF_WINDOW_SIZE)) {
            workbook.setCompressTempFiles(true);

            ExcelStyles styles = new ExcelStyles(workbook);

            // ── Sheet 1: Tổng quan & Xu hướng ─────────────────────────────────
            SXSSFSheet sheetSummary = workbook.createSheet("Tổng quan doanh thu");
            sheetSummary.trackAllColumnsForAutoSizing();

            int rowIdx = 0;
            // Tiêu đề
            Row titleRow = sheetSummary.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BÁO CÁO DOANH THU");
            titleCell.setCellStyle(styles.titleStyle);

            // Thông tin chung
            createMetaRow(sheetSummary, rowIdx++, "Kỳ báo cáo:", from.format(DATE_TIME_FORMATTER) + "  đến  " + to.format(DATE_TIME_FORMATTER), styles);
            createMetaRow(sheetSummary, rowIdx++, "Chi nhánh:", branchNames.isEmpty() ? "Tất cả chi nhánh" : branchNames, styles);
            createMetaRow(sheetSummary, rowIdx++, "Thời điểm xuất:", LocalDateTime.now().format(DATE_TIME_FORMATTER), styles);
            createMetaRow(sheetSummary, rowIdx++, "Nhóm xu hướng theo:", "MONTH".equalsIgnoreCase(groupBy) ? "Tháng" : "Ngày", styles);
            rowIdx++; // Dòng trống

            // Bảng 1: Chỉ số tổng quan
            Row sec1Row = sheetSummary.createRow(rowIdx++);
            Cell sec1Cell = sec1Row.createCell(0);
            sec1Cell.setCellValue("1. CHỈ SỐ DOANH THU CHÍNH");
            sec1Cell.setCellStyle(styles.sectionStyle);

            String[] kpiHeaders = {"Chỉ số", "Giá trị kỳ này", "Giá trị kỳ trước", "Tăng trưởng"};
            createHeaderRow(sheetSummary, rowIdx++, kpiHeaders, styles);

            createKpiRow(sheetSummary, rowIdx++, "Tổng doanh thu", summary.getTotalRevenue(), summary.getPreviousPeriodRevenue(), summary.getRevenueGrowthPercent(), styles, true);
            createKpiRow(sheetSummary, rowIdx++, "Tổng số hóa đơn", summary.getTotalInvoices(), null, null, styles, false);
            createKpiRow(sheetSummary, rowIdx++, "Tổng số lượt khách", summary.getTotalGuests(), null, null, styles, false);
            createKpiRow(sheetSummary, rowIdx++, "Doanh thu TB/hóa đơn", summary.getAvgRevenuePerInvoice(), null, null, styles, true);
            rowIdx++; // Dòng trống

            // Bảng 2: Doanh thu theo phương thức thanh toán
            Row sec2Row = sheetSummary.createRow(rowIdx++);
            Cell sec2Cell = sec2Row.createCell(0);
            sec2Cell.setCellValue("2. THEO PHƯƠNG THỨC THANH TOÁN");
            sec2Cell.setCellStyle(styles.sectionStyle);

            String[] methodHeaders = {"Phương thức thanh toán", "Doanh thu (VNĐ)", "Tỷ trọng (%)"};
            createHeaderRow(sheetSummary, rowIdx++, methodHeaders, styles);

            BigDecimal totalRev = summary.getTotalRevenue() != null ? summary.getTotalRevenue() : BigDecimal.ZERO;
            if (summary.getRevenueByPaymentMethod() != null && !summary.getRevenueByPaymentMethod().isEmpty()) {
                for (Map.Entry<String, BigDecimal> entry : summary.getRevenueByPaymentMethod().entrySet()) {
                    Row r = sheetSummary.createRow(rowIdx++);
                    Cell c0 = r.createCell(0);
                    c0.setCellValue(translatePaymentMethod(entry.getKey()));
                    c0.setCellStyle(styles.dataLeftStyle);

                    Cell c1 = r.createCell(1);
                    c1.setCellValue(entry.getValue().doubleValue());
                    c1.setCellStyle(styles.currencyStyle);

                    Cell c2 = r.createCell(2);
                    double share = totalRev.compareTo(BigDecimal.ZERO) > 0
                            ? entry.getValue().doubleValue() / totalRev.doubleValue() : 0.0;
                    c2.setCellValue(share);
                    c2.setCellStyle(styles.percentStyle);
                }
            } else {
                Row r = sheetSummary.createRow(rowIdx++);
                Cell c0 = r.createCell(0);
                c0.setCellValue("Không có dữ liệu");
                c0.setCellStyle(styles.dataLeftStyle);
            }
            rowIdx++; // Dòng trống

            // Bảng 3: Xu hướng theo ngày/tháng
            Row sec3Row = sheetSummary.createRow(rowIdx++);
            Cell sec3Cell = sec3Row.createCell(0);
            sec3Cell.setCellValue("3. XU HƯỚNG THEO THỜI GIAN");
            sec3Cell.setCellStyle(styles.sectionStyle);

            String[] trendHeaders = {"Thời gian", "Doanh thu (VNĐ)", "Số hóa đơn"};
            createHeaderRow(sheetSummary, rowIdx++, trendHeaders, styles);

            if (summary.getTrend() != null && !summary.getTrend().isEmpty()) {
                for (RevenueReportResponse.RevenueTrendPoint point : summary.getTrend()) {
                    Row r = sheetSummary.createRow(rowIdx++);
                    Cell c0 = r.createCell(0);
                    c0.setCellValue(point.getLabel());
                    c0.setCellStyle(styles.dataCenterStyle);

                    Cell c1 = r.createCell(1);
                    c1.setCellValue(point.getRevenue().doubleValue());
                    c1.setCellStyle(styles.currencyStyle);

                    Cell c2 = r.createCell(2);
                    c2.setCellValue(point.getInvoiceCount());
                    c2.setCellStyle(styles.integerStyle);
                }
            }
            autoSizeColumns(sheetSummary, 4);

            // ── Sheet 2: Chi tiết hóa đơn (Chunking) ──────────────────────────
            SXSSFSheet sheetInvoices = workbook.createSheet("Chi tiết hóa đơn");

            String[] invoiceHeaders = {
                    "STT", "Mã hóa đơn", "Chi nhánh", "Bàn", "Thời gian lập",
                    "Tiền món (VNĐ)", "Khuyến mãi (VNĐ)", "Chiết khấu tay (VNĐ)",
                    "Phí DV (VNĐ)", "VAT (VNĐ)", "Tổng cộng (VNĐ)", "Trạng thái",
                    "Phương thức thanh toán", "Ghi chú"
            };
            createHeaderRow(sheetInvoices, 0, invoiceHeaders, styles);

            // Truy vấn hóa đơn theo chunking để chống tràn RAM
            int page = 0;
            int invoiceRowIdx = 1;
            Page<Invoice> invoicePage;
            do {
                invoicePage = invoiceRepository.findInvoicesForReport(
                        branchIds, PAID_STATUSES, from, to, PageRequest.of(page++, CHUNK_SIZE));

                for (Invoice inv : invoicePage.getContent()) {
                    Row r = sheetInvoices.createRow(invoiceRowIdx);

                    Cell c0 = r.createCell(0);
                    c0.setCellValue(invoiceRowIdx);
                    c0.setCellStyle(styles.dataCenterStyle);

                    Cell c1 = r.createCell(1);
                    c1.setCellValue(inv.getInvoiceNumber());
                    c1.setCellStyle(styles.dataCenterStyle);

                    Cell c2 = r.createCell(2);
                    c2.setCellValue(inv.getBranch() != null ? inv.getBranch().getName() : "");
                    c2.setCellStyle(styles.dataLeftStyle);

                    Cell c3 = r.createCell(3);
                    c3.setCellValue(getTableName(inv));
                    c3.setCellStyle(styles.dataCenterStyle);

                    Cell c4 = r.createCell(4);
                    c4.setCellValue(inv.getCreatedAt() != null ? inv.getCreatedAt().format(DATE_TIME_FORMATTER) : "");
                    c4.setCellStyle(styles.dataCenterStyle);

                    Cell c5 = r.createCell(5);
                    c5.setCellValue(inv.getSubTotalBeforeDiscount() != null ? inv.getSubTotalBeforeDiscount().doubleValue() : 0.0);
                    c5.setCellStyle(styles.currencyStyle);

                    Cell c6 = r.createCell(6);
                    c6.setCellValue(inv.getTotalPromotionDiscount() != null ? inv.getTotalPromotionDiscount().doubleValue() : 0.0);
                    c6.setCellStyle(styles.currencyStyle);

                    Cell c7 = r.createCell(7);
                    c7.setCellValue(inv.getTotalManualDiscount() != null ? inv.getTotalManualDiscount().doubleValue() : 0.0);
                    c7.setCellStyle(styles.currencyStyle);

                    Cell c8 = r.createCell(8);
                    c8.setCellValue(inv.getServiceFee() != null ? inv.getServiceFee().doubleValue() : 0.0);
                    c8.setCellStyle(styles.currencyStyle);

                    Cell c9 = r.createCell(9);
                    c9.setCellValue(inv.getVatAmount() != null ? inv.getVatAmount().doubleValue() : 0.0);
                    c9.setCellStyle(styles.currencyStyle);

                    Cell c10 = r.createCell(10);
                    c10.setCellValue(inv.getGrandTotal() != null ? inv.getGrandTotal().doubleValue() : 0.0);
                    c10.setCellStyle(styles.currencyStyle);

                    Cell c11 = r.createCell(11);
                    c11.setCellValue(translateInvoiceStatus(inv.getStatus()));
                    c11.setCellStyle(styles.dataCenterStyle);

                    Cell c12 = r.createCell(12);
                    c12.setCellValue(getPaymentMethods(inv));
                    c12.setCellStyle(styles.dataCenterStyle);

                    Cell c13 = r.createCell(13);
                    c13.setCellValue(inv.getInvoiceNote() != null ? inv.getInvoiceNote() : "");
                    c13.setCellStyle(styles.dataLeftStyle);

                    invoiceRowIdx++;
                }

                log.debug("Đã xuất chunk hóa đơn trang {}, tổng bản ghi: {}", page - 1, invoiceRowIdx - 1);
            } while (invoicePage.hasNext());

            // Thiết lập độ rộng cột cho sheet chi tiết
            int[] colWidths = {2000, 4500, 6000, 3000, 5000, 4000, 4000, 4000, 3500, 3500, 4500, 3500, 5000, 6000};
            for (int i = 0; i < colWidths.length; i++) {
                sheetInvoices.setColumnWidth(i, colWidths[i]);
            }

            workbook.write(outputStream);
            workbook.dispose();
        } catch (IOException e) {
            log.error("Lỗi ghi Excel báo cáo doanh thu: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo file Excel báo cáo doanh thu", e);
        }
    }

    private void exportRevenueReportCsv(
            List<Long> branchIds,
            LocalDateTime from,
            LocalDateTime to,
            String groupBy,
            RevenueReportResponse summary,
            String branchNames,
            OutputStream outputStream) {

        try {
            writeUtf8Bom(outputStream);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

            writer.write("BÁO CÁO DOANH THU");
            writer.newLine();
            writer.write("Kỳ báo cáo," + escapeCsv(from.format(DATE_TIME_FORMATTER) + " đến " + to.format(DATE_TIME_FORMATTER)));
            writer.newLine();
            writer.write("Chi nhánh," + escapeCsv(branchNames.isEmpty() ? "Tất cả chi nhánh" : branchNames));
            writer.newLine();
            writer.write("Thời điểm xuất," + escapeCsv(LocalDateTime.now().format(DATE_TIME_FORMATTER)));
            writer.newLine();
            writer.newLine();

            // Tổng quan
            writer.write("TỔNG QUAN");
            writer.newLine();
            writer.write("Tổng doanh thu (VNĐ)," + summary.getTotalRevenue());
            writer.newLine();
            writer.write("Tổng số hóa đơn," + summary.getTotalInvoices());
            writer.newLine();
            writer.write("Tổng số khách," + summary.getTotalGuests());
            writer.newLine();
            writer.write("Doanh thu TB/hóa đơn (VNĐ)," + summary.getAvgRevenuePerInvoice());
            writer.newLine();
            if (summary.getRevenueGrowthPercent() != null) {
                writer.write("Tăng trưởng (%)," + String.format("%.2f%%", summary.getRevenueGrowthPercent()));
                writer.newLine();
            }
            writer.newLine();

            // Chi tiết hóa đơn (Chunking)
            writer.write("CHI TIẾT HÓA ĐƠN");
            writer.newLine();
            writer.write("STT,Mã hóa đơn,Chi nhánh,Bàn,Thời gian lập,Tiền món (VNĐ),Khuyến mãi (VNĐ),Chiết khấu tay (VNĐ),Phí DV (VNĐ),VAT (VNĐ),Tổng cộng (VNĐ),Trạng thái,Phương thức thanh toán,Ghi chú");
            writer.newLine();

            int page = 0;
            int stt = 1;
            Page<Invoice> invoicePage;
            do {
                invoicePage = invoiceRepository.findInvoicesForReport(
                        branchIds, PAID_STATUSES, from, to, PageRequest.of(page++, CHUNK_SIZE));

                for (Invoice inv : invoicePage.getContent()) {
                    String line = String.join(",",
                            String.valueOf(stt++),
                            escapeCsv(inv.getInvoiceNumber()),
                            escapeCsv(inv.getBranch() != null ? inv.getBranch().getName() : ""),
                            escapeCsv(getTableName(inv)),
                            escapeCsv(inv.getCreatedAt() != null ? inv.getCreatedAt().format(DATE_TIME_FORMATTER) : ""),
                            inv.getSubTotalBeforeDiscount() != null ? inv.getSubTotalBeforeDiscount().toString() : "0",
                            inv.getTotalPromotionDiscount() != null ? inv.getTotalPromotionDiscount().toString() : "0",
                            inv.getTotalManualDiscount() != null ? inv.getTotalManualDiscount().toString() : "0",
                            inv.getServiceFee() != null ? inv.getServiceFee().toString() : "0",
                            inv.getVatAmount() != null ? inv.getVatAmount().toString() : "0",
                            inv.getGrandTotal() != null ? inv.getGrandTotal().toString() : "0",
                            escapeCsv(translateInvoiceStatus(inv.getStatus())),
                            escapeCsv(getPaymentMethods(inv)),
                            escapeCsv(inv.getInvoiceNote() != null ? inv.getInvoiceNote() : "")
                    );
                    writer.write(line);
                    writer.newLine();
                }

                // Chunk flush ra stream
                writer.flush();
            } while (invoicePage.hasNext());

        } catch (IOException e) {
            log.error("Lỗi ghi CSV báo cáo doanh thu: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo file CSV báo cáo doanh thu", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. HOURLY REPORT: EXCEL & CSV
    // ═══════════════════════════════════════════════════════════════════════

    private void exportHourlyReportExcel(
            LocalDateTime from,
            LocalDateTime to,
            HourlyRevenueResponse hourly,
            String branchNames,
            OutputStream outputStream) {

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSF_WINDOW_SIZE)) {
            workbook.setCompressTempFiles(true);
            ExcelStyles styles = new ExcelStyles(workbook);

            SXSSFSheet sheet = workbook.createSheet("Doanh thu theo khung giờ");
            sheet.trackAllColumnsForAutoSizing();

            int rowIdx = 0;
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BÁO CÁO DOANH THU THEO KHUNG GIỜ");
            titleCell.setCellStyle(styles.titleStyle);

            createMetaRow(sheet, rowIdx++, "Kỳ báo cáo:", from.format(DATE_TIME_FORMATTER) + "  đến  " + to.format(DATE_TIME_FORMATTER), styles);
            createMetaRow(sheet, rowIdx++, "Chi nhánh:", branchNames.isEmpty() ? "Tất cả chi nhánh" : branchNames, styles);
            createMetaRow(sheet, rowIdx++, "Thời điểm xuất:", LocalDateTime.now().format(DATE_TIME_FORMATTER), styles);
            rowIdx++;

            // Bảng 1: Theo khung giờ trong ngày
            Row sec1Row = sheet.createRow(rowIdx++);
            Cell sec1Cell = sec1Row.createCell(0);
            sec1Cell.setCellValue("1. PHÂN BỔ THEO 24 KHUNG GIỜ");
            sec1Cell.setCellStyle(styles.sectionStyle);

            String[] hourHeaders = {"Giờ", "Khung giờ", "Doanh thu (VNĐ)", "Số bàn phục vụ"};
            createHeaderRow(sheet, rowIdx++, hourHeaders, styles);

            if (hourly.getHourlySlots() != null) {
                for (HourlyRevenueResponse.HourSlot slot : hourly.getHourlySlots()) {
                    Row r = sheet.createRow(rowIdx++);
                    Cell c0 = r.createCell(0);
                    c0.setCellValue(slot.getHour());
                    c0.setCellStyle(styles.dataCenterStyle);

                    Cell c1 = r.createCell(1);
                    c1.setCellValue(slot.getLabel());
                    c1.setCellStyle(styles.dataCenterStyle);

                    Cell c2 = r.createCell(2);
                    c2.setCellValue(slot.getRevenue() != null ? slot.getRevenue().doubleValue() : 0.0);
                    c2.setCellStyle(styles.currencyStyle);

                    Cell c3 = r.createCell(3);
                    c3.setCellValue(slot.getTableCount());
                    c3.setCellStyle(styles.integerStyle);
                }
            }
            rowIdx++;

            // Bảng 2: Theo ngày trong tuần
            Row sec2Row = sheet.createRow(rowIdx++);
            Cell sec2Cell = sec2Row.createCell(0);
            sec2Cell.setCellValue("2. PHÂN BỔ THEO THỨ TRONG TUẦN");
            sec2Cell.setCellStyle(styles.sectionStyle);

            String[] dowHeaders = {"Thứ trong tuần", "Doanh thu (VNĐ)", "Số hóa đơn"};
            createHeaderRow(sheet, rowIdx++, dowHeaders, styles);

            if (hourly.getDayOfWeekSlots() != null) {
                for (HourlyRevenueResponse.DayOfWeekSlot slot : hourly.getDayOfWeekSlots()) {
                    Row r = sheet.createRow(rowIdx++);
                    Cell c0 = r.createCell(0);
                    c0.setCellValue(slot.getLabel());
                    c0.setCellStyle(styles.dataLeftStyle);

                    Cell c1 = r.createCell(1);
                    c1.setCellValue(slot.getRevenue() != null ? slot.getRevenue().doubleValue() : 0.0);
                    c1.setCellStyle(styles.currencyStyle);

                    Cell c2 = r.createCell(2);
                    c2.setCellValue(slot.getInvoiceCount());
                    c2.setCellStyle(styles.integerStyle);
                }
            }

            autoSizeColumns(sheet, 4);

            workbook.write(outputStream);
            workbook.dispose();
        } catch (IOException e) {
            log.error("Lỗi ghi Excel báo cáo khung giờ: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo file Excel báo cáo khung giờ", e);
        }
    }

    private void exportHourlyReportCsv(
            LocalDateTime from,
            LocalDateTime to,
            HourlyRevenueResponse hourly,
            String branchNames,
            OutputStream outputStream) {

        try {
            writeUtf8Bom(outputStream);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

            writer.write("BÁO CÁO DOANH THU THEO KHUNG GIỜ");
            writer.newLine();
            writer.write("Kỳ báo cáo," + escapeCsv(from.format(DATE_TIME_FORMATTER) + " đến " + to.format(DATE_TIME_FORMATTER)));
            writer.newLine();
            writer.write("Chi nhánh," + escapeCsv(branchNames.isEmpty() ? "Tất cả chi nhánh" : branchNames));
            writer.newLine();
            writer.write("Thời điểm xuất," + escapeCsv(LocalDateTime.now().format(DATE_TIME_FORMATTER)));
            writer.newLine();
            writer.newLine();

            writer.write("PHÂN BỔ THEO 24 KHUNG GIỜ");
            writer.newLine();
            writer.write("Giờ,Khung giờ,Doanh thu (VNĐ),Số bàn phục vụ");
            writer.newLine();

            if (hourly.getHourlySlots() != null) {
                for (HourlyRevenueResponse.HourSlot slot : hourly.getHourlySlots()) {
                    writer.write(String.join(",",
                            String.valueOf(slot.getHour()),
                            escapeCsv(slot.getLabel()),
                            slot.getRevenue() != null ? slot.getRevenue().toString() : "0",
                            String.valueOf(slot.getTableCount())
                    ));
                    writer.newLine();
                }
            }
            writer.newLine();

            writer.write("PHÂN BỔ THEO THỨ TRONG TUẦN");
            writer.newLine();
            writer.write("Thứ trong tuần,Doanh thu (VNĐ),Số hóa đơn");
            writer.newLine();

            if (hourly.getDayOfWeekSlots() != null) {
                for (HourlyRevenueResponse.DayOfWeekSlot slot : hourly.getDayOfWeekSlots()) {
                    writer.write(String.join(",",
                            escapeCsv(slot.getLabel()),
                            slot.getRevenue() != null ? slot.getRevenue().toString() : "0",
                            String.valueOf(slot.getInvoiceCount())
                    ));
                    writer.newLine();
                }
            }

            writer.flush();
        } catch (IOException e) {
            log.error("Lỗi ghi CSV báo cáo khung giờ: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo file CSV báo cáo khung giờ", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. BRANCH COMPARISON: EXCEL & CSV
    // ═══════════════════════════════════════════════════════════════════════

    private void exportBranchComparisonExcel(
            LocalDateTime from,
            LocalDateTime to,
            BranchRevenueCompareResponse comparison,
            Long restaurantId,
            OutputStream outputStream) {

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSF_WINDOW_SIZE)) {
            workbook.setCompressTempFiles(true);
            ExcelStyles styles = new ExcelStyles(workbook);

            SXSSFSheet sheet = workbook.createSheet("So sánh chi nhánh");
            sheet.trackAllColumnsForAutoSizing();

            int rowIdx = 0;
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BÁO CÁO SO SÁNH DOANH THU CHI NHÁNH");
            titleCell.setCellStyle(styles.titleStyle);

            createMetaRow(sheet, rowIdx++, "Kỳ báo cáo:", from.format(DATE_TIME_FORMATTER) + "  đến  " + to.format(DATE_TIME_FORMATTER), styles);
            createMetaRow(sheet, rowIdx++, "Mã nhà hàng:", String.valueOf(restaurantId), styles);
            createMetaRow(sheet, rowIdx++, "Thời điểm xuất:", LocalDateTime.now().format(DATE_TIME_FORMATTER), styles);
            rowIdx++;

            // Chỉ số trung bình toàn hệ thống
            Row sec1Row = sheet.createRow(rowIdx++);
            Cell sec1Cell = sec1Row.createCell(0);
            sec1Cell.setCellValue("1. CHỈ SỐ TRUNG BÌNH TOÀN NHÀ HÀNG");
            sec1Cell.setCellStyle(styles.sectionStyle);

            String[] avgHeaders = {"Chỉ số", "Giá trị trung bình"};
            createHeaderRow(sheet, rowIdx++, avgHeaders, styles);

            createSimpleRow(sheet, rowIdx++, "Doanh thu trung bình chi nhánh", comparison.getAvgRevenue() != null ? comparison.getAvgRevenue().doubleValue() : 0.0, styles.currencyStyle, styles);
            createSimpleRow(sheet, rowIdx++, "Giá trị trung bình / hóa đơn", comparison.getAvgInvoiceValue() != null ? comparison.getAvgInvoiceValue().doubleValue() : 0.0, styles.currencyStyle, styles);
            createSimpleRow(sheet, rowIdx++, "Tỷ lệ hủy món trung bình", (comparison.getAvgCancelRate() != null ? comparison.getAvgCancelRate() : 0.0) / 100.0, styles.percentStyle, styles);
            rowIdx++;

            // Bảng chi tiết từng chi nhánh
            Row sec2Row = sheet.createRow(rowIdx++);
            Cell sec2Cell = sec2Row.createCell(0);
            sec2Cell.setCellValue("2. BẢNG XẾP HẠNG CHI NHÁNH");
            sec2Cell.setCellStyle(styles.sectionStyle);

            String[] branchHeaders = {
                    "Xếp hạng", "Mã CN", "Tên chi nhánh", "Doanh thu (VNĐ)",
                    "Số hóa đơn", "TB/hóa đơn (VNĐ)", "Hủy lỗi nhà hàng",
                    "Hủy khách đổi ý", "Tỷ lệ hủy (%)", "Cảnh báo bất thường"
            };
            createHeaderRow(sheet, rowIdx++, branchHeaders, styles);

            if (comparison.getBranches() != null) {
                for (BranchRevenueCompareResponse.BranchStat b : comparison.getBranches()) {
                    Row r = sheet.createRow(rowIdx++);

                    Cell c0 = r.createCell(0);
                    c0.setCellValue(b.getRank());
                    c0.setCellStyle(styles.dataCenterStyle);

                    Cell c1 = r.createCell(1);
                    c1.setCellValue(b.getBranchId());
                    c1.setCellStyle(styles.dataCenterStyle);

                    Cell c2 = r.createCell(2);
                    c2.setCellValue(b.getBranchName());
                    c2.setCellStyle(styles.dataLeftStyle);

                    Cell c3 = r.createCell(3);
                    c3.setCellValue(b.getTotalRevenue() != null ? b.getTotalRevenue().doubleValue() : 0.0);
                    c3.setCellStyle(styles.currencyStyle);

                    Cell c4 = r.createCell(4);
                    c4.setCellValue(b.getTotalInvoices());
                    c4.setCellStyle(styles.integerStyle);

                    Cell c5 = r.createCell(5);
                    c5.setCellValue(b.getAvgInvoiceValue() != null ? b.getAvgInvoiceValue().doubleValue() : 0.0);
                    c5.setCellStyle(styles.currencyStyle);

                    Cell c6 = r.createCell(6);
                    c6.setCellValue(b.getCancelByRestaurantCount());
                    c6.setCellStyle(styles.integerStyle);

                    Cell c7 = r.createCell(7);
                    c7.setCellValue(b.getCancelByCustomerCount());
                    c7.setCellStyle(styles.integerStyle);

                    Cell c8 = r.createCell(8);
                    c8.setCellValue((b.getTotalCancelRate() != null ? b.getTotalCancelRate() : 0.0) / 100.0);
                    c8.setCellStyle(styles.percentStyle);

                    Cell c9 = r.createCell(9);
                    c9.setCellValue(b.isHasAnomaly() ? (b.getAnomalyNote() != null ? b.getAnomalyNote() : "Bất thường") : "Bình thường");
                    c9.setCellStyle(b.isHasAnomaly() ? styles.dangerStyle : styles.dataCenterStyle);
                }
            }

            autoSizeColumns(sheet, branchHeaders.length);

            workbook.write(outputStream);
            workbook.dispose();
        } catch (IOException e) {
            log.error("Lỗi ghi Excel so sánh chi nhánh: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo file Excel so sánh chi nhánh", e);
        }
    }

    private void exportBranchComparisonCsv(
            LocalDateTime from,
            LocalDateTime to,
            BranchRevenueCompareResponse comparison,
            Long restaurantId,
            OutputStream outputStream) {

        try {
            writeUtf8Bom(outputStream);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

            writer.write("BÁO CÁO SO SÁNH DOANH THU CHI NHÁNH");
            writer.newLine();
            writer.write("Kỳ báo cáo," + escapeCsv(from.format(DATE_TIME_FORMATTER) + " đến " + to.format(DATE_TIME_FORMATTER)));
            writer.newLine();
            writer.write("Mã nhà hàng," + restaurantId);
            writer.newLine();
            writer.write("Thời điểm xuất," + escapeCsv(LocalDateTime.now().format(DATE_TIME_FORMATTER)));
            writer.newLine();
            writer.newLine();

            writer.write("CHỈ SỐ TRUNG BÌNH TOÀN NHÀ HÀNG");
            writer.newLine();
            writer.write("Doanh thu trung bình (VNĐ)," + comparison.getAvgRevenue());
            writer.newLine();
            writer.write("Giá trị TB/hóa đơn (VNĐ)," + comparison.getAvgInvoiceValue());
            writer.newLine();
            writer.write("Tỷ lệ hủy trung bình (%)," + String.format("%.2f%%", comparison.getAvgCancelRate() != null ? comparison.getAvgCancelRate() : 0.0));
            writer.newLine();
            writer.newLine();

            writer.write("BẢNG XẾP HẠNG CHI NHÁNH");
            writer.newLine();
            writer.write("Xếp hạng,Mã CN,Tên chi nhánh,Doanh thu (VNĐ),Số hóa đơn,TB/hóa đơn (VNĐ),Hủy lỗi nhà hàng,Hủy khách đổi ý,Tỷ lệ hủy (%),Cảnh báo bất thường");
            writer.newLine();

            if (comparison.getBranches() != null) {
                for (BranchRevenueCompareResponse.BranchStat b : comparison.getBranches()) {
                    String line = String.join(",",
                            String.valueOf(b.getRank()),
                            String.valueOf(b.getBranchId()),
                            escapeCsv(b.getBranchName()),
                            b.getTotalRevenue() != null ? b.getTotalRevenue().toString() : "0",
                            String.valueOf(b.getTotalInvoices()),
                            b.getAvgInvoiceValue() != null ? b.getAvgInvoiceValue().toString() : "0",
                            String.valueOf(b.getCancelByRestaurantCount()),
                            String.valueOf(b.getCancelByCustomerCount()),
                            String.format("%.2f%%", b.getTotalCancelRate() != null ? b.getTotalCancelRate() : 0.0),
                            escapeCsv(b.isHasAnomaly() ? (b.getAnomalyNote() != null ? b.getAnomalyNote() : "Bất thường") : "Bình thường")
                    );
                    writer.write(line);
                    writer.newLine();
                }
            }

            writer.flush();
        } catch (IOException e) {
            log.error("Lỗi ghi CSV so sánh chi nhánh: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo file CSV so sánh chi nhánh", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers & Styles
    // ═══════════════════════════════════════════════════════════════════════

    private void recordAuditLog(String reportName, String format, Long branchId) {
        try {
            Long currentRestaurantId = securityUtils.getCurrentRestaurantId();
            auditLogService.record(AuditEvent.builder()
                    .actionType(AuditAction.EXPORT)
                    .entityType("Report")
                    .reason("Xuất " + reportName + " định dạng " + format.toUpperCase())
                    .branchId(branchId)
                    .restaurantId(currentRestaurantId)
                    .build());
        } catch (Exception e) {
            log.warn("Không thể ghi audit log xuất báo cáo: {}", e.getMessage());
        }
    }

    private void writeUtf8Bom(OutputStream outputStream) throws IOException {
        outputStream.write(0xEF);
        outputStream.write(0xBB);
        outputStream.write(0xBF);
    }

    private String escapeCsv(Object val) {
        if (val == null) return "";
        String str = val.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    private String getTableName(Invoice inv) {
        if (inv.getTableSession() != null && inv.getTableSession().getTable() != null) {
            return inv.getTableSession().getTable().getName();
        }
        return "";
    }

    private String getPaymentMethods(Invoice inv) {
        if (inv.getPayments() == null || inv.getPayments().isEmpty()) {
            return "";
        }
        return inv.getPayments().stream()
                .map(p -> translatePaymentMethod(p.getMethod() != null ? p.getMethod().name() : ""))
                .distinct()
                .collect(Collectors.joining("; "));
    }

    private String translatePaymentMethod(String method) {
        if (method == null) return "";
        return switch (method.toUpperCase()) {
            case "CASH" -> "Tiền mặt";
            case "BANK_TRANSFER", "VIETQR" -> "Chuyển khoản (VietQR)";
            case "CREDIT_CARD", "CARD" -> "Thẻ ngân hàng";
            case "MOMO" -> "Ví MoMo";
            case "ZALOPAY" -> "Ví ZaloPay";
            case "VNPAY" -> "VNPay";
            default -> method;
        };
    }

    private String translateInvoiceStatus(InvoiceStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PAID -> "Đã thanh toán";
            case ISSUED -> "Đã phát hành";
            case PENDING -> "Chờ thanh toán";
            case CANCELLED -> "Đã hủy";
            case REFUNDED -> "Đã hoàn tiền";
            default -> status.name();
        };
    }

    private void validateBranchIds(List<Long> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            throw new IllegalArgumentException("Danh sách chi nhánh không được để trống");
        }
    }

    private void createHeaderRow(SXSSFSheet sheet, int rowIdx, String[] headers, ExcelStyles styles) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < headers.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.headerStyle);
        }
    }

    private void createMetaRow(SXSSFSheet sheet, int rowIdx, String label, String value, ExcelStyles styles) {
        Row r = sheet.createRow(rowIdx);
        Cell c0 = r.createCell(0);
        c0.setCellValue(label);
        c0.setCellStyle(styles.metaLabelStyle);

        Cell c1 = r.createCell(1);
        c1.setCellValue(value);
        c1.setCellStyle(styles.metaValueStyle);
    }

    private void createKpiRow(
            SXSSFSheet sheet,
            int rowIdx,
            String label,
            Object currentValue,
            Object prevValue,
            Double growth,
            ExcelStyles styles,
            boolean isCurrency) {

        Row r = sheet.createRow(rowIdx);
        Cell c0 = r.createCell(0);
        c0.setCellValue(label);
        c0.setCellStyle(styles.dataLeftStyle);

        Cell c1 = r.createCell(1);
        if (currentValue instanceof BigDecimal bd) {
            c1.setCellValue(bd.doubleValue());
            c1.setCellStyle(isCurrency ? styles.currencyStyle : styles.integerStyle);
        } else if (currentValue instanceof Number num) {
            c1.setCellValue(num.doubleValue());
            c1.setCellStyle(isCurrency ? styles.currencyStyle : styles.integerStyle);
        } else {
            c1.setCellValue(0);
            c1.setCellStyle(styles.integerStyle);
        }

        Cell c2 = r.createCell(2);
        if (prevValue instanceof BigDecimal bd) {
            c2.setCellValue(bd.doubleValue());
            c2.setCellStyle(isCurrency ? styles.currencyStyle : styles.integerStyle);
        } else if (prevValue instanceof Number num) {
            c2.setCellValue(num.doubleValue());
            c2.setCellStyle(isCurrency ? styles.currencyStyle : styles.integerStyle);
        } else {
            c2.setCellValue("-");
            c2.setCellStyle(styles.dataCenterStyle);
        }

        Cell c3 = r.createCell(3);
        if (growth != null) {
            c3.setCellValue(growth / 100.0);
            c3.setCellStyle(styles.percentStyle);
        } else {
            c3.setCellValue("-");
            c3.setCellStyle(styles.dataCenterStyle);
        }
    }

    private void createSimpleRow(SXSSFSheet sheet, int rowIdx, String label, double value, CellStyle valueStyle, ExcelStyles styles) {
        Row r = sheet.createRow(rowIdx);
        Cell c0 = r.createCell(0);
        c0.setCellValue(label);
        c0.setCellStyle(styles.dataLeftStyle);

        Cell c1 = r.createCell(1);
        c1.setCellValue(value);
        c1.setCellStyle(valueStyle);
    }

    private void autoSizeColumns(SXSSFSheet sheet, int colCount) {
        for (int i = 0; i < colCount; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.max(width + 1000, 3500));
        }
    }

    // ── Helper class gói gọn các kiểu CellStyle của Apache POI ─────────────
    private static class ExcelStyles {
        final CellStyle headerStyle;
        final CellStyle titleStyle;
        final CellStyle sectionStyle;
        final CellStyle metaLabelStyle;
        final CellStyle metaValueStyle;
        final CellStyle currencyStyle;
        final CellStyle integerStyle;
        final CellStyle percentStyle;
        final CellStyle dataLeftStyle;
        final CellStyle dataCenterStyle;
        final CellStyle dangerStyle;

        ExcelStyles(Workbook wb) {
            DataFormat format = wb.createDataFormat();

            // Font tiêu đề
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());

            titleStyle = wb.createCellStyle();
            titleStyle.setFont(titleFont);

            // Font section
            Font secFont = wb.createFont();
            secFont.setBold(true);
            secFont.setFontHeightInPoints((short) 12);
            secFont.setColor(IndexedColors.DARK_BLUE.getIndex());

            sectionStyle = wb.createCellStyle();
            sectionStyle.setFont(secFont);

            // Header table
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            addBorders(headerStyle);

            // Meta
            Font metaLabelFont = wb.createFont();
            metaLabelFont.setBold(true);
            metaLabelStyle = wb.createCellStyle();
            metaLabelStyle.setFont(metaLabelFont);

            metaValueStyle = wb.createCellStyle();

            // Currency
            currencyStyle = wb.createCellStyle();
            currencyStyle.setDataFormat(format.getFormat("#,##0"));
            currencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            addBorders(currencyStyle);

            // Integer
            integerStyle = wb.createCellStyle();
            integerStyle.setDataFormat(format.getFormat("#,##0"));
            integerStyle.setAlignment(HorizontalAlignment.RIGHT);
            addBorders(integerStyle);

            // Percent
            percentStyle = wb.createCellStyle();
            percentStyle.setDataFormat(format.getFormat("0.0%"));
            percentStyle.setAlignment(HorizontalAlignment.RIGHT);
            addBorders(percentStyle);

            // Data Left
            dataLeftStyle = wb.createCellStyle();
            dataLeftStyle.setAlignment(HorizontalAlignment.LEFT);
            addBorders(dataLeftStyle);

            // Data Center
            dataCenterStyle = wb.createCellStyle();
            dataCenterStyle.setAlignment(HorizontalAlignment.CENTER);
            addBorders(dataCenterStyle);

            // Danger
            Font dangerFont = wb.createFont();
            dangerFont.setBold(true);
            dangerFont.setColor(IndexedColors.RED.getIndex());
            dangerStyle = wb.createCellStyle();
            dangerStyle.setFont(dangerFont);
            dangerStyle.setAlignment(HorizontalAlignment.CENTER);
            addBorders(dangerStyle);
        }

        private void addBorders(CellStyle style) {
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            style.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            style.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            style.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        }
    }
}
