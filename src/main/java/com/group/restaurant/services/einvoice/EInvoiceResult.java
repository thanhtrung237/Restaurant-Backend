package com.group.restaurant.services.einvoice;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;


@Getter
@Builder
public class EInvoiceResult {

  
    private final String ref;

    /**
     * Thời điểm nhà cung cấp xác nhận phát hành thành công.
     */
    private final LocalDateTime issuedAt;

    /**
     * Tên nhà cung cấp đã xử lý (viettel, vnpt, misa, stub...).
     */
    private final String providerName;
}
