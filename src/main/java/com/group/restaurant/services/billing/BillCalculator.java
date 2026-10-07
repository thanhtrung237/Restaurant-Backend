package com.group.restaurant.services.billing;

import com.group.restaurant.enums.DiscountType;
import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.mappers.SoftDeleteSafe;
import com.group.restaurant.models.*;
import com.group.restaurant.repositories.ManualDiscountRepository;
import com.group.restaurant.repositories.OrderRepository;
import com.group.restaurant.services.promotion.PromotionEngine;
import com.group.restaurant.services.promotion.PromotionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * BỘ TÍNH TIỀN DUY NHẤT của hệ thống (BR-13). createInvoice, tính lại hóa đơn khi có chiết khấu
 * tay và endpoint xem tạm tính đều gọi vào đây; FE chỉ hiển thị, không tự tính.
 *
 * Bộ tính KHÔNG có tác dụng phụ (không trừ lượt khuyến mãi, không ghi DB) nên gọi bao nhiêu lần
 * cũng được. Việc trừ/hoàn lượt KM đơn do bên gọi (InvoiceServiceImpl) làm khi chốt hóa đơn.
 */
@Component
@RequiredArgsConstructor
public class BillCalculator {

    private final PromotionEngine promotionEngine;
    private final OrderRepository orderRepository;
    private final ManualDiscountRepository manualDiscountRepository;

    // ---------------------------------------------------------------- Cách chọn KM đơn

    /** Cách chọn khuyến mãi cấp hóa đơn. */
    public static final class PromotionSelection {
        enum Mode {AUTO, PREFER, NONE, LOCKED}

        final Mode mode;
        final Long promotionId;
        final String promotionName;
        final BigDecimal lockedAmount;

        private PromotionSelection(Mode mode, Long id, String name, BigDecimal amount) {
            this.mode = mode;
            this.promotionId = id;
            this.promotionName = name;
            this.lockedAmount = amount;
        }

        /** Engine tự chọn KM tốt nhất (BR-22, BR-23). */
        public static PromotionSelection auto() {
            return new PromotionSelection(Mode.AUTO, null, null, null);
        }

        /** Ưu tiên KM Thu ngân chọn; không hợp lệ thì rơi về tự chọn. */
        public static PromotionSelection prefer(Long promotionId) {
            return promotionId == null ? auto()
                    : new PromotionSelection(Mode.PREFER, promotionId, null, null);
        }

        /** Không áp KM đơn (ví dụ KM vừa hết lượt). */
        public static PromotionSelection none() {
            return new PromotionSelection(Mode.NONE, null, null, null);
        }

        /**
         * Giữ nguyên KM đơn đã chốt trên hóa đơn PENDING (số tiền và lượt đã trừ), dùng khi chỉ
         * tính lại vì chiết khấu tay đổi — không chọn lại để khỏi đổi số khách đã thấy.
         */
        public static PromotionSelection locked(Long promotionId, String name, BigDecimal amount) {
            return new PromotionSelection(Mode.LOCKED, promotionId, name, amount);
        }
    }

    // ---------------------------------------------------------------- Làm tròn

    /** Làm tròn HALF_UP về đồng. */
    public static BigDecimal round(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(0, RoundingMode.HALF_UP);
    }

    /** amount × percent / 100, làm tròn HALF_UP về đồng. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        if (amount == null || percent == null) return BigDecimal.ZERO;
        return amount.multiply(percent).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- Dòng tính tiền

    /**
     * Dòng có tính tiền không.
     *  - cancelBeforeKitchen / cancelAfterKitchen lỗi nhà hàng → isBillable = false → không tính
     *  - cancelAfterKitchen khách đổi ý → isBillable = true → vẫn tính (BR-24)
     */
    public static boolean shouldChargeDetail(OrderDetail d) {
        if (d.getStatus() != OrderDetailStatus.CANCELLED) return true;
        return Boolean.TRUE.equals(d.getIsBillable());
    }

    /**
     * Khuyến mãi món của một dòng (đồng, không âm).
     */
    private static BigDecimal itemDiscountOf(OrderDetail d) {
        BigDecimal v = d.getPromotionDiscountAmount();
        return v == null || v.signum() < 0 ? BigDecimal.ZERO : round(v);
    }

    /**
     * Tiền hàng GỐC của một dòng = số tiền SAU khuyến mãi (price) + khuyến mãi món.
     * Quy ước hệ thống: OrderDetail.price luôn là số sau KM món (đã gồm topping).
     */
    private static BigDecimal grossOf(OrderDetail d) {
        BigDecimal net;
        if (d.getPrice() != null) {
            net = d.getPrice();
        } else {
            BigDecimal qty = d.getWeightKg() != null
                    ? d.getWeightKg()
                    : BigDecimal.valueOf(d.getQuantity() != null ? d.getQuantity() : 1);
            net = d.getPriceAtOrderTime() != null ? d.getPriceAtOrderTime().multiply(qty) : BigDecimal.ZERO;
        }
        BigDecimal promo = d.getPromotionDiscountAmount() != null ? d.getPromotionDiscountAmount() : BigDecimal.ZERO;
        return round(net.add(promo));
    }

    // ---------------------------------------------------------------- Nạp dữ liệu

    /**
     * Toàn bộ chiết khấu tay của phiên: gắn theo order của phiên HOẶC theo hóa đơn (luồng Thu ngân
     * tạo hóa đơn trước rồi mới tạo chiết khấu với invoiceId). Loại trùng theo id.
     */
    public List<ManualDiscount> loadManualDiscounts(Long tableSessionId, Long invoiceIdOrNull) {
        Map<Long, ManualDiscount> byId = new LinkedHashMap<>();
        for (Order o : orderRepository.findByTableSessionId(tableSessionId)) {
            for (ManualDiscount md : manualDiscountRepository.findByOrderId(o.getId())) {
                byId.putIfAbsent(md.getId(), md);
            }
        }
        if (invoiceIdOrNull != null) {
            for (ManualDiscount md : manualDiscountRepository.findByInvoiceId(invoiceIdOrNull)) {
                byId.putIfAbsent(md.getId(), md);
            }
        }
        return new ArrayList<>(byId.values());
    }

    // ---------------------------------------------------------------- Tính tiền

    /**
     * Tính toàn bộ hóa đơn từ dòng món + chiết khấu tay + snapshot thuế/phí của phiên.
     *
     * @param session         phiên bàn (snapshot serviceFeeRateAtOpen / vatRateAtOpen — BR-08)
     * @param branch          chi nhánh (cờ "phí dịch vụ chịu VAT" và nhà hàng để tìm KM)
     * @param details         mọi dòng món của phiên (hàm tự lọc dòng tính tiền)
     * @param manualDiscounts chiết khấu tay (xem {@link #loadManualDiscounts})
     * @param selection       cách chọn KM đơn
     */
    public BillBreakdown calculate(TableSession session, Branch branch, List<OrderDetail> details,
                                   List<ManualDiscount> manualDiscounts, PromotionSelection selection) {
        // 1) Tiền hàng gốc và KM món, làm tròn từng dòng
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal itemPromo = BigDecimal.ZERO;
        boolean hasPendingWeight = false;
        for (OrderDetail d : details) {
            if (d.getStatus() == OrderDetailStatus.PENDING_WEIGHT) hasPendingWeight = true;
            if (!shouldChargeDetail(d)) continue;
            BigDecimal lineGross = grossOf(d);
            BigDecimal lineItemPromo = itemDiscountOf(d).min(lineGross);
            gross = gross.add(lineGross);
            itemPromo = itemPromo.add(lineItemPromo);
        }
        BigDecimal afterItemPromo = gross.subtract(itemPromo);

        // 2) KM đơn — tính trên (gross − KM món), đã kẹp maxDiscount trong engine (QĐ-13)
        BigDecimal orderPromo = BigDecimal.ZERO;
        Long orderPromoId = null;
        String orderPromoName = null;
        PromotionResult pr = resolveOrderPromotion(session, branch, selection, afterItemPromo);
        if (pr != null && pr.hasPromotion()) {
            orderPromo = round(pr.getDiscountAmount()).min(afterItemPromo).max(BigDecimal.ZERO);
            if (orderPromo.signum() > 0) {
                orderPromoId = pr.getPromotionId();
                orderPromoName = pr.getPromotionName();
            }
        }
        BigDecimal afterPromo = afterItemPromo.subtract(orderPromo);

        // 3) Chiết khấu tay — tính trên (gross − KM món − KM đơn), tổng kẹp ≤ số còn lại
        BigDecimal manual = BigDecimal.ZERO;
        for (ManualDiscount md : manualDiscounts) {
            manual = manual.add(manualAmount(md, afterPromo));
        }
        manual = manual.min(afterPromo).max(BigDecimal.ZERO);
        BigDecimal base = afterPromo.subtract(manual);

        // 4) Phí dịch vụ và VAT theo snapshot của phiên (BR-08)
        BigDecimal serviceFeeRate = session.getServiceFeeRateAtOpen() != null
                ? session.getServiceFeeRateAtOpen() : BigDecimal.ZERO;
        BigDecimal serviceFee = percentOf(base, serviceFeeRate);

        BigDecimal vatRate = session.getVatRateAtOpen() != null
                ? session.getVatRateAtOpen() : BigDecimal.ZERO;
        boolean vatOnFee = isVatOnServiceFee(branch);
        BigDecimal vatBase = vatOnFee ? base.add(serviceFee) : base;
        BigDecimal vatAmount = percentOf(vatBase, vatRate);

        BigDecimal grand = base.add(serviceFee).add(vatAmount).max(BigDecimal.ZERO);

        return BillBreakdown.builder()
                .grossSubTotal(gross)
                .itemPromotionDiscount(itemPromo)
                .orderPromotionDiscount(orderPromo)
                .orderPromotionId(orderPromoId)
                .orderPromotionName(orderPromoName)
                .manualDiscount(manual)
                .amountAfterDiscount(base)
                .serviceFeeRate(serviceFeeRate)
                .serviceFee(serviceFee)
                .vatRate(vatRate)
                .vatOnServiceFee(vatOnFee)
                .vatBase(vatBase)
                .vatAmount(vatAmount)
                .grandTotal(grand)
                .hasPendingWeight(hasPendingWeight)
                .build();
    }

    /**
     * Dựng breakdown từ một hóa đơn ĐÃ LẬP, dùng đúng các số đã lưu (không tính lại) để hóa đơn
     * lịch sử (PAID/ISSUED, bất biến theo BR-09) luôn hiển thị đúng số đã chốt dù sau đó KM,
     * cờ VAT hay chi nhánh có đổi. Chỉ phần tách KM món / KM đơn và cờ VAT là suy ra.
     */
    public BillBreakdown describeInvoice(Invoice invoice, List<OrderDetail> details) {
        BigDecimal gross = nz(invoice.getSubTotalBeforeDiscount());
        BigDecimal totalPromo = nz(invoice.getTotalPromotionDiscount());
        BigDecimal manual = nz(invoice.getTotalManualDiscount());
        BigDecimal serviceFee = nz(invoice.getServiceFee());
        BigDecimal vatAmount = nz(invoice.getVatAmount());
        BigDecimal grand = nz(invoice.getGrandTotal());

        BigDecimal itemPromo = BigDecimal.ZERO;
        boolean pendingWeight = false;
        for (OrderDetail d : details) {
            if (d.getStatus() == OrderDetailStatus.PENDING_WEIGHT) pendingWeight = true;
            if (shouldChargeDetail(d)) itemPromo = itemPromo.add(itemDiscountOf(d));
        }
        itemPromo = itemPromo.min(totalPromo);
        BigDecimal orderPromo = totalPromo.subtract(itemPromo);

        BigDecimal base = gross.subtract(totalPromo).subtract(manual).max(BigDecimal.ZERO);

        // Hóa đơn là dữ liệu lịch sử: phiên/chi nhánh gốc có thể đã bị xóa mềm → đọc qua SoftDeleteSafe.
        TableSession session = invoice.getTableSession();
        BigDecimal serviceFeeRate = nz(SoftDeleteSafe.get(session, TableSession::getServiceFeeRateAtOpen));
        BigDecimal vatRate = nz(SoftDeleteSafe.get(session, TableSession::getVatRateAtOpen));

        boolean vatOnFee = inferVatOnServiceFee(base, serviceFee, vatRate, vatAmount,
                isVatOnServiceFee(invoice.getBranch()));
        BigDecimal vatBase = vatOnFee ? base.add(serviceFee) : base;

        Promotion promo = invoice.getPromotion();
        return BillBreakdown.builder()
                .grossSubTotal(gross)
                .itemPromotionDiscount(itemPromo)
                .orderPromotionDiscount(orderPromo)
                .orderPromotionId(SoftDeleteSafe.get(promo, Promotion::getId))
                .orderPromotionName(SoftDeleteSafe.get(promo, Promotion::getName))
                .manualDiscount(manual)
                .amountAfterDiscount(base)
                .serviceFeeRate(serviceFeeRate)
                .serviceFee(serviceFee)
                .vatRate(vatRate)
                .vatOnServiceFee(vatOnFee)
                .vatBase(vatBase)
                .vatAmount(vatAmount)
                .grandTotal(grand)
                .hasPendingWeight(pendingWeight)
                .build();
    }

    // ---------------------------------------------------------------- Chi tiết nội bộ

    private PromotionResult resolveOrderPromotion(TableSession session, Branch branch,
                                                  PromotionSelection selection, BigDecimal afterItemPromo) {
        PromotionSelection sel = selection != null ? selection : PromotionSelection.auto();
        switch (sel.mode) {
            case NONE:
                return PromotionResult.noPromotion();
            case LOCKED:
                if (sel.promotionId == null || sel.lockedAmount == null) return PromotionResult.noPromotion();
                return new PromotionResult(sel.promotionId, sel.promotionName, sel.lockedAmount, 0);
            default:
                break;
        }
        Long branchId = session.getBranch() != null ? session.getBranch().getId() : branch.getId();
        Long restaurantId = branch.getRestaurant().getId();
        double base = afterItemPromo.doubleValue();
        if (sel.mode == PromotionSelection.Mode.PREFER) {
            PromotionResult preferred = promotionEngine.calculateOrderPromotionById(
                    branchId, restaurantId, sel.promotionId, base);
            if (preferred.hasPromotion()) return preferred;
        }
        return promotionEngine.calculateOrderPromotion(branchId, restaurantId, base);
    }

    private static BigDecimal manualAmount(ManualDiscount md, BigDecimal base) {
        if (md.getValue() == null || md.getValue().signum() <= 0) return BigDecimal.ZERO;
        if (md.getType() == DiscountType.PERCENTAGE) {
            return percentOf(base, md.getValue());
        }
        return round(md.getValue()); // FIXED_AMOUNT
    }

    /**
     * Cờ "phí dịch vụ có chịu VAT" của chi nhánh (QĐ-17). null hiểu là BẬT — mặc định của hệ
     * thống là phí dịch vụ chịu VAT (TT 219/2013 Điều 7).
     */
    public static boolean isVatOnServiceFee(Branch branch) {
        Boolean flag = SoftDeleteSafe.get(branch, Branch::getVatAppliedOnServiceFee);
        return flag == null || flag;
    }

    /**
     * Suy cờ VAT đã dùng lúc lập hóa đơn từ chính số tiền đã lưu, để hóa đơn cũ không đổi theo
     * cờ hiện tại của chi nhánh. Không phân biệt được (hai cách ra cùng số) thì dùng cờ hiện tại.
     */
    private static boolean inferVatOnServiceFee(BigDecimal base, BigDecimal serviceFee, BigDecimal vatRate,
                                                BigDecimal storedVat, boolean fallback) {
        BigDecimal onFee = percentOf(base.add(serviceFee), vatRate);
        BigDecimal notOnFee = percentOf(base, vatRate);
        boolean matchOnFee = onFee.compareTo(storedVat) == 0;
        boolean matchNotOnFee = notOnFee.compareTo(storedVat) == 0;
        if (matchOnFee && !matchNotOnFee) return true;
        if (matchNotOnFee && !matchOnFee) return false;
        return fallback;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
