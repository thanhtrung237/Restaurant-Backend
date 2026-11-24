package com.group.restaurant.dtos.response.invoice;

import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.enums.TableSessionStatus;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tạm tính của một phiên bàn — GET /table-sessions/{id}/bill-preview.
 *
 * Cùng bộ field breakdown với {@link InvoiceResponse} (do cùng một BillCalculator sinh ra) nên
 * FE dùng chung một component hiển thị. Mọi số tiền là số nguyên đồng.
 *
 * Nếu phiên đã có hóa đơn (không tính đã hủy) thì các số được lấy từ chính hóa đơn đó
 * (invoiceId / invoiceStatus khác null) để tạm tính không bao giờ lệch với hóa đơn.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillPreviewResponse {

    private Long tableSessionId;
    private TableSessionStatus sessionStatus;

    /** null nếu chưa có hóa đơn. */
    private Long invoiceId;

    private InvoiceStatus invoiceStatus;

    // ----- Breakdown theo thứ tự BR-13 -----
    private BigDecimal grossSubTotal;
    private BigDecimal itemPromotionDiscount;
    private BigDecimal orderPromotionDiscount;
    private Long orderPromotionId;
    private String orderPromotionName;
    private BigDecimal manualDiscount;

    /** Tiền sau giảm = gross − KM món − KM đơn − chiết khấu tay. */
    private BigDecimal amountAfterDiscount;

    private BigDecimal serviceFeeRate;
    private BigDecimal serviceFee;
    private BigDecimal vatRate;
    private boolean vatOnServiceFee;
    private BigDecimal vatBase;
    private BigDecimal vatAmount;
    private BigDecimal grandTotal;

    /** Còn món cân chưa cân — tạm tính chưa đầy đủ. */
    private boolean hasPendingWeight;
}
