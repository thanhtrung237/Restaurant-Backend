package com.group.restaurant.dtos.response.invoice;

import com.group.restaurant.dtos.response.payment.PaymentResponse;
import com.group.restaurant.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponse {

    private Long id;
    private Long branchId;
    private String branchName;
    private Long tableSessionId;
    private String tableName;
    private Boolean takeaway;
    private Long promotionId;
    private String promotionName;
    private String invoiceNumber;
    private String invoiceNote;
    private BigDecimal subTotalBeforeDiscount;
    private BigDecimal totalPromotionDiscount;
    private BigDecimal totalManualDiscount;
    private BigDecimal serviceFee;
    private BigDecimal vatAmount;
    private BigDecimal grandTotal;
    private boolean quoteShared;
    private boolean discountApprovalPending;

    // ----- Breakdown đầy đủ theo thứ tự BR-13 (cùng bộ field với BillPreviewResponse) -----
    // Suy ra từ phiên và dòng món, không cần đổi schema. Các field cũ ở trên giữ nguyên để tương
    // thích;
    // subTotalBeforeDiscount nay chính là grossSubTotal (tiền hàng gốc, chưa trừ KM món).
    private BigDecimal grossSubTotal;
    private BigDecimal itemPromotionDiscount;
    private BigDecimal orderPromotionDiscount;
    private Long orderPromotionId;
    private String orderPromotionName;
    private BigDecimal manualDiscount;

    /** Tiền sau giảm = gross − KM món − KM đơn − chiết khấu tay. */
    private BigDecimal amountAfterDiscount;

    private BigDecimal serviceFeeRate;
    private BigDecimal vatRate;
    private boolean vatOnServiceFee;
    private BigDecimal vatBase;
    private InvoiceStatus status;
    private List<PaymentResponse> payments;
    private BigDecimal totalPaid;
    private BigDecimal remainingAmount;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    //  Thông tin hóa đơn điện tử
    private String eInvoiceRef;
    private LocalDateTime eInvoiceIssuedAt;
    private String eInvoiceIssuedByName;
    private String eInvoiceIssuedByEmployeeCode;
    private String taxCodeUsed;
    private String eInvoiceProvider;
    private int eInvoiceAttemptCount;
}
