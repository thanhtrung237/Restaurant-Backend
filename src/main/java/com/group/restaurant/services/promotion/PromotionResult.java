package com.group.restaurant.services.promotion;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả tính khuyến mãi
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromotionResult {

    private Long promotionId;
    private String promotionName;
    private BigDecimal discountAmount;
    private int priority;

    public static PromotionResult noPromotion() {
        return new PromotionResult(null, null, BigDecimal.ZERO, 0);
    }

    public boolean hasPromotion() {
        return promotionId != null
                && discountAmount != null
                && discountAmount.compareTo(BigDecimal.ZERO) > 0;
    }
}
