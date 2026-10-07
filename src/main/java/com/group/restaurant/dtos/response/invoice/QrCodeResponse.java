package com.group.restaurant.dtos.response.invoice;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Response cho sinh QR VietQR (FR-PAY-03).
 * Trả về URL ảnh QR với số tiền còn lại (remainingAmount).
 */
@Getter
@Setter
@Builder
public class QrCodeResponse {

    private Long invoiceId;
    private String invoiceNumber;

    // Số tiền còn lại cần thanh toán
    private BigDecimal remainingAmount;

    // Tổng hóa đơn
    private BigDecimal grandTotal;

    // Đã trả rồi
    private BigDecimal totalPaid;

    // Thông tin tài khoản chi nhánh
    private String bankName;
    private String accountNo;
    private String accountName;

    // URL ảnh QR từ VietQR (public API, không cần API key)
    private String qrImageUrl;

    // Nội dung chuyển khoản (để khách điền vào nếu ngân hàng không hỗ trợ QR)
    private String transferContent;
}
