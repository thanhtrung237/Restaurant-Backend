package com.group.restaurant.services;

import com.group.restaurant.dtos.request.invoice.InvoiceCreateRequest;
import com.group.restaurant.dtos.response.invoice.InvoiceListResponse;
import com.group.restaurant.dtos.response.invoice.InvoiceResponse;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.dtos.response.invoice.QrCodeResponse;

import java.util.List;

public interface InvoiceService {

    InvoiceResponse createInvoice(InvoiceCreateRequest request);

    InvoiceResponse getInvoiceById(Long id);

    InvoiceResponse getInvoiceByTableSession(Long tableSessionId);

    List<InvoiceListResponse> getInvoicesByBranch(Long branchId, InvoiceStatus status);

    void cancelInvoice(Long id);

    QrCodeResponse generateQrCode(Long invoiceId);

    QrCodeResponse generateQrCodeByTableSession(Long tableSessionId);

    /**
     * Tính lại hóa đơn PENDING sau khi chiết khấu tay được tạo/xóa (luồng Thu ngân: lập hóa đơn
     * trước, chiết khấu sau). Giữ nguyên KM đơn đã chốt. Hóa đơn không còn PENDING (PAID / ISSUED
     * / CANCELLED — BR-09) hoặc phiên không còn WAITING_FOR_PAY thì không làm gì.
     */
    void recalculatePendingInvoice(Long invoiceId);

    /**

     * Điều kiện gọi: hóa đơn phải ở trạng thái PAID (đã thu đủ tiền).
     * Thành công → trạng thái chuyển sang ISSUED, session được CLOSE.
     * Thất bại → ném ConflictException, session KHÔNG bị đóng (Thu ngân thử lại).
     *
     * @param invoiceId ID hóa đơn cần phát hành
     * @return InvoiceResponse với trạng thái ISSUED và thông tin ref HĐĐT
     */
    InvoiceResponse issueEInvoice(Long invoiceId);
}
