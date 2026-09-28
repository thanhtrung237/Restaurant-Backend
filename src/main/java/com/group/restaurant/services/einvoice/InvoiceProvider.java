package com.group.restaurant.services.einvoice;

import com.group.restaurant.models.Invoice;


public interface InvoiceProvider {

    /**
     * Phát hành hóa đơn điện tử lên cơ quan thuế.
     *
     * @param invoice hóa đơn nội bộ đã PAID, chứa đủ thông tin tính tiền
     * @return kết quả phát hành
     * @throws EInvoiceException nếu phát hành thất bại (cho phép Thu ngân thử lại)
     */
    EInvoiceResult issue(Invoice invoice) throws EInvoiceException;


    String providerName();
}
