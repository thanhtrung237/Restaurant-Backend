package com.group.restaurant.services;

import com.group.restaurant.dtos.response.invoice.BillPreviewResponse;

public interface BillService {

    /**
     * Xem tạm tính của một phiên bàn — CHỈ ĐỌC, không trừ lượt khuyến mãi, không tạo hóa đơn.
     *
     * Phiên chưa có hóa đơn: tính trực tiếp bằng BillCalculator từ dòng món, chiết khấu tay và
     * snapshot thuế/phí của phiên. Phiên đã có hóa đơn (không tính đã hủy): trả đúng các số đã
     * chốt trên hóa đơn đó để tạm tính và hóa đơn không bao giờ lệch nhau.
     */
    BillPreviewResponse previewByTableSession(Long tableSessionId);
}
