package com.group.restaurant.services.billing;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Kết quả tính tiền của MỘT phiên bàn — nguồn sự thật duy nhất cho hóa đơn, xem tạm tính,
 * QR và Payment. Mọi khoản đều là SỐ NGUYÊN ĐỒNG (scale 0), đã làm tròn HALF_UP từng dòng
 * rồi mới cộng, nên các dòng luôn cộng lại đúng bằng grandTotal.
 *
 * Thứ tự dòng (BR-13):
 * <pre>
 *   grossSubTotal            Tiền hàng gốc = Σ (đơn giá + topping) × SL|kg, chỉ dòng tính tiền
 * − itemPromotionDiscount    KM món
 * − orderPromotionDiscount   KM đơn (tính trên gross − KM món, kẹp maxDiscount)
 * − manualDiscount           Chiết khấu tay (tính trên gross − KM món − KM đơn, kẹp ≤ số còn lại)
 * = amountAfterDiscount      Tiền cơ sở
 * + serviceFee               = amountAfterDiscount × serviceFeeRate
 * + vatAmount                = vatBase × vatRate
 * = grandTotal
 * </pre>
 */
@Value
@Builder
public class BillBreakdown {

    BigDecimal grossSubTotal;
    BigDecimal itemPromotionDiscount;
    BigDecimal orderPromotionDiscount;
    Long orderPromotionId;
    String orderPromotionName;
    BigDecimal manualDiscount;

    /** Tiền sau giảm (tiền cơ sở để tính phí dịch vụ và VAT). */
    BigDecimal amountAfterDiscount;

    /** Phần trăm, ví dụ 5 nghĩa là 5%. */
    BigDecimal serviceFeeRate;
    BigDecimal serviceFee;

    /** Phần trăm, ví dụ 8 nghĩa là 8%. */
    BigDecimal vatRate;
    /** true = VAT tính trên (tiền cơ sở + phí dịch vụ); false = chỉ trên tiền cơ sở. */
    boolean vatOnServiceFee;
    BigDecimal vatBase;
    BigDecimal vatAmount;

    BigDecimal grandTotal;

    /** Còn dòng món bán theo cân chưa cân — tiền hàng chưa đầy đủ, chưa nên lập hóa đơn. */
    boolean hasPendingWeight;

    /** Tổng khuyến mãi = KM món + KM đơn (khớp cột invoice.total_promotion_discount). */
    public BigDecimal getTotalPromotionDiscount() {
        return itemPromotionDiscount.add(orderPromotionDiscount);
    }
}
