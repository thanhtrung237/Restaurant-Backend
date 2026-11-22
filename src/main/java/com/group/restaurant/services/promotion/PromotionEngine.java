package com.group.restaurant.services.promotion;

import com.group.restaurant.enums.ApplyLevel;
import com.group.restaurant.enums.PromotionStatus;
import com.group.restaurant.models.Promotion;
import com.group.restaurant.repositories.PromotionBranchRepository;
import com.group.restaurant.repositories.PromotionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Engine tính toán khuyến mãi - dùng chung cho menu, giỏ hàng, order và invoice
 *
 * Nghiệp vụ BR-22: Hệ thống tự động áp khuyến mãi khi đủ điều kiện
 * Nghiệp vụ BR-23: Ưu tiên áp dụng theo priority, cùng priority thì chọn lợi nhất cho khách
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionEngine {

    private final PromotionRepository promotionRepository;
    private final PromotionBranchRepository promotionBranchRepository;
    private final ObjectMapper objectMapper;

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Lấy tất cả khuyến mãi đang chạy cho một chi nhánh
     * Đã lọc: ACTIVE, trong khoảng ngày, đúng thứ, đúng giờ, đúng chi nhánh, còn lượt
     */
    public List<Promotion> getActivePromotions(Long branchId, Long restaurantId) {
        // Lấy tất cả khuyến mãi ACTIVE của nhà hàng
        List<Promotion> allPromotions = promotionRepository.findByRestaurantIdAndStatus(
                restaurantId, PromotionStatus.ACTIVE);

        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        LocalDate today = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();
        DayOfWeek currentDay = now.getDayOfWeek();

        return allPromotions.stream()
                .filter(p -> isPromotionApplicable(p, branchId, today, currentTime, currentDay))
                .collect(Collectors.toList());
    }

    /**
     * Kiểm tra khuyến mãi có áp dụng được không
     */
    private boolean isPromotionApplicable(Promotion promotion, Long branchId,
                                          LocalDate today, LocalTime currentTime, DayOfWeek currentDay) {
        // 1. Kiểm tra ngày
        if (today.isBefore(promotion.getStartDate()) || today.isAfter(promotion.getEndDate())) {
            return false;
        }

        // 2. Kiểm tra thứ trong tuần
        if (promotion.getDaysOfWeek() != null && !promotion.getDaysOfWeek().isEmpty()) {
            String[] days = promotion.getDaysOfWeek().split(",");
            String dayName = currentDay.name();
            boolean matchDay = Arrays.stream(days)
                    .map(String::trim)
                    .anyMatch(d -> {
                        String upper = d.toUpperCase();
                        return upper.equalsIgnoreCase(dayName)
                                || dayName.startsWith(upper)
                                || upper.startsWith(dayName.substring(0, Math.min(3, dayName.length())));
                    });
            if (!matchDay) {
                return false;
            }
        }

        // 3. Kiểm tra khung giờ
        if (promotion.getTimeWindow() != null && !promotion.getTimeWindow().isEmpty()) {
            if (!isWithinTimeWindow(promotion.getTimeWindow(), currentTime)) {
                return false;
            }
        }

        // 4. Kiểm tra chi nhánh
        if (promotion.isAllBranches()) {
            // Toàn hệ thống - luôn áp dụng
            return true;
        } else {
            // Kiểm tra chi nhánh cụ thể
            return promotionBranchRepository.existsByBranchIdAndPromotionId(branchId, promotion.getId());
        }
    }

    /**
     * Kiểm tra giờ hiện tại có trong khung giờ không
     * @param timeWindow format "HH:mm-HH:mm" ví dụ "11:00-14:00" hoặc "11:00-0:00"
     */
    private boolean isWithinTimeWindow(String timeWindow, LocalTime currentTime) {
        try {
            String[] parts = timeWindow.split("-");
            if (parts.length != 2) return true; // Invalid format -> allow

            LocalTime start = parseTimeFlexible(parts[0].trim());
            LocalTime end = parseTimeFlexible(parts[1].trim());

            if (end.equals(LocalTime.MIDNIGHT) || end.isBefore(start)) {
                return !currentTime.isBefore(start) || !currentTime.isAfter(end);
            }
            return !currentTime.isBefore(start) && !currentTime.isAfter(end);
        } catch (Exception e) {
            log.warn("Invalid timeWindow format: {}", timeWindow, e);
            return true; // Invalid format -> allow
        }
    }

    private LocalTime parseTimeFlexible(String s) {
        if (s != null && s.length() == 4 && s.charAt(1) == ':') {
            s = "0" + s;
        }
        return LocalTime.parse(s);
    }

    /**
     * Tính khuyến mãi cấp món cho một món cụ thể
     * @return số tiền giảm giá (0 nếu không có KM)
     */
    public PromotionResult calculateItemPromotion(Long branchId, Long restaurantId,
                                                  Long dishId, Long categoryId,
                                                  int quantity, double itemSubtotal) {
        List<Promotion> itemPromotions = getActivePromotions(branchId, restaurantId).stream()
                .filter(p -> p.getApplyLevel() == ApplyLevel.ITEM)
                .filter(p -> isPromotionStillHasUsage(p))
                .collect(Collectors.toList());

        if (itemPromotions.isEmpty()) {
            return PromotionResult.noPromotion();
        }

        // Lọc các KM áp dụng cho món này
        List<PromotionResult> applicableResults = new ArrayList<>();

        for (Promotion promo : itemPromotions) {
            if (isItemMatchPromotion(promo, dishId, categoryId)) {
                // Kiểm tra điều kiện
                if (checkItemCondition(promo, quantity, itemSubtotal)) {
                    BigDecimal discount = calculateDiscount(promo, quantity, itemSubtotal);
                    if (discount.compareTo(BigDecimal.ZERO) > 0) {
                        applicableResults.add(new PromotionResult(promo.getId(), promo.getName(),
                                discount, promo.getPriority()));
                    }
                }
            }
        }

        if (applicableResults.isEmpty()) {
            return PromotionResult.noPromotion();
        }

        // Chọn KM tốt nhất (BR-23): priority cao nhất, cùng priority thì lợi nhất
        return applicableResults.stream()
                .max(Comparator.comparing(PromotionResult::getPriority)
                        .thenComparing(PromotionResult::getDiscountAmount))
                .orElse(PromotionResult.noPromotion());
    }

    /**
     * Tính khuyến mãi cấp hóa đơn
     * @param orderSubtotal Tổng tiền hàng DOPO khuyến mãi cấp món
     */
    public PromotionResult calculateOrderPromotion(Long branchId, Long restaurantId,
                                                   double orderSubtotal) {
        return calculateOrderPromotion(branchId, restaurantId, orderSubtotal, null, java.util.Set.of());
    }

    public PromotionResult calculateOrderPromotion(Long branchId, Long restaurantId,
                                                   double orderSubtotal, Long reservedId, java.util.Set<Long> excludedIds) {
        List<Promotion> orderPromotions = getActivePromotions(branchId, restaurantId).stream()
                .filter(p -> p.getApplyLevel() == ApplyLevel.INVOICE)
                .filter(p -> p.getCode() == null || p.getCode().isBlank())
                .filter(p -> !excludedIds.contains(p.getId()))
                .filter(p -> p.getId().equals(reservedId) || isPromotionStillHasUsage(p))
                .collect(Collectors.toList());

        if (orderPromotions.isEmpty()) {
            return PromotionResult.noPromotion();
        }

        List<PromotionResult> applicableResults = new ArrayList<>();

        for (Promotion promo : orderPromotions) {
            if (checkOrderCondition(promo, orderSubtotal)) {
                BigDecimal discount = calculateDiscount(promo, 1, orderSubtotal);
                if (discount.compareTo(BigDecimal.ZERO) > 0) {
                    applicableResults.add(new PromotionResult(promo.getId(), promo.getName(),
                            discount, promo.getPriority()));
                }
            }
        }

        if (applicableResults.isEmpty()) {
            return PromotionResult.noPromotion();
        }

        // Chọn KM tốt nhất
        return applicableResults.stream()
                .max(Comparator.comparing(PromotionResult::getDiscountAmount)
                        .thenComparing(PromotionResult::getPriority)
                        .thenComparing(PromotionResult::getPromotionId, Comparator.reverseOrder()))
                .orElse(PromotionResult.noPromotion());
    }

    /**
     * Kiểm tra món có khớp với KM không
     */
    private boolean isItemMatchPromotion(Promotion promo, Long dishId, Long categoryId) {
        try {
            JsonNode condition = objectMapper.readTree(promo.getCondition());

            // Kiểm tra target
            String target = condition.has("target") ? condition.get("target").asText() : "all_order";

            if ("all_order".equals(target)) {
                return true;
            }

            if ("category".equals(target)) {
                JsonNode categoryIds = condition.get("categoryIds");
                if (categoryIds != null && categoryIds.isArray()) {
                    for (JsonNode idNode : categoryIds) {
                        if (idNode.asLong() == categoryId) {
                            return true;
                        }
                    }
                }
                return false;
            }

            if ("specific_dish".equals(target)) {
                JsonNode dishIds = condition.get("dishIds");
                if (dishIds != null && dishIds.isArray()) {
                    for (JsonNode idNode : dishIds) {
                        if (idNode.asLong() == dishId) {
                            return true;
                        }
                    }
                }
                return false;
            }

            return false;
        } catch (Exception e) {
            log.warn("Error parsing promotion condition for promotion {}", promo.getId(), e);
            return false;
        }
    }

    /**
     * Kiểm tra điều kiện món
     */
    private boolean checkItemCondition(Promotion promo, int quantity, double subtotal) {
        try {
            JsonNode condition = objectMapper.readTree(promo.getCondition());

            // Kiểm tra minQty
            if (condition.has("minQty")) {
                int minQty = condition.get("minQty").asInt();
                if (quantity < minQty) {
                    return false;
                }
            }

            // Kiểm tra minOrderValue
            if (condition.has("minOrderValue")) {
                double minValue = condition.get("minOrderValue").asDouble();
                if (subtotal < minValue) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("Error checking item condition for promotion {}", promo.getId(), e);
            return false;
        }
    }

    /**
     * Kiểm tra điều kiện hóa đơn
     */
    public boolean checkOrderCondition(Promotion promo, double orderSubtotal) {
        try {
            JsonNode condition = objectMapper.readTree(promo.getCondition());

            // Kiểm tra minOrderValue
            if (condition.has("minOrderValue")) {
                double minValue = condition.get("minOrderValue").asDouble();
                if (orderSubtotal < minValue) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("Error checking order condition for promotion {}", promo.getId(), e);
            return false;
        }
    }

    /**
     * Tính số tiền giảm giá, đã kẹp bởi trần giảm tối đa `maxDiscount` (QĐ-13) nếu KM có khai báo.
     *
     * Trần được đọc từ JSON `action` của khuyến mãi (khóa `maxDiscount`, chấp nhận thêm
     * `maxDiscountAmount`) nên không cần đổi schema. Không khai báo hoặc <= 0 nghĩa là không có trần.
     * Trần áp cho mọi loại (percentage / fixed_amount / gift_dish), cả KM món lẫn KM đơn.
     */
    public BigDecimal calculateDiscount(Promotion promo, int quantity, double subtotal) {
        BigDecimal raw = calculateRawDiscount(promo, quantity, subtotal);
        BigDecimal cap = readMaxDiscount(promo);
        if (cap != null && cap.signum() > 0 && raw.compareTo(cap) > 0) {
            return cap;
        }
        return raw;
    }

    /**
     * Đọc trần giảm tối đa của KM; null nếu không có.
     */
    public BigDecimal readMaxDiscount(Promotion promo) {
        try {
            if (promo.getAction() == null || promo.getAction().isBlank()) return null;
            JsonNode action = objectMapper.readTree(promo.getAction());
            JsonNode node = action.has("maxDiscount") ? action.get("maxDiscount") : action.get("maxDiscountAmount");
            if (node == null || node.isNull() || !node.isNumber()) return null;
            BigDecimal cap = node.decimalValue();
            return cap.signum() > 0 ? cap : null;
        } catch (Exception e) {
            log.warn("Error reading maxDiscount for promotion {}", promo.getId(), e);
            return null;
        }
    }

    /**
     * Áp một KM CẤP HÓA ĐƠN do người dùng chọn (theo id) lên `orderSubtotal`.
     *
     * Chỉ chấp nhận KM đang chạy cho đúng chi nhánh/nhà hàng, đúng cấp INVOICE, còn lượt và
     * thỏa điều kiện. Không thỏa thì trả noPromotion() để bên gọi rơi về chọn tự động.
     * KHÔNG trừ lượt — bên gọi tự trừ khi chốt hóa đơn.
     */
    public PromotionResult calculateOrderPromotionById(Long branchId, Long restaurantId,
                                                       Long promotionId, double orderSubtotal) {
        if (promotionId == null) return PromotionResult.noPromotion();
        return getActivePromotions(branchId, restaurantId).stream()
                .filter(p -> promotionId.equals(p.getId()))
                .filter(p -> p.getApplyLevel() == ApplyLevel.INVOICE)
                .filter(this::isPromotionStillHasUsage)
                .filter(p -> checkOrderCondition(p, orderSubtotal))
                .findFirst()
                .map(p -> new PromotionResult(p.getId(), p.getName(),
                        calculateDiscount(p, 1, orderSubtotal), p.getPriority()))
                .filter(PromotionResult::hasPromotion)
                .orElse(PromotionResult.noPromotion());
    }

    private BigDecimal calculateRawDiscount(Promotion promo, int quantity, double subtotal) {
        try {
            JsonNode action = objectMapper.readTree(promo.getAction());
            String type = action.has("type") ? action.get("type").asText() : "fixed_amount";

            if ("percentage".equals(type)) {
                double percent = action.has("value") ? action.get("value").asDouble() : 0;
                percent = Math.min(Math.max(percent, 0.0), 100.0);
                return BigDecimal.valueOf(subtotal * percent / 100.0);
            }

            if ("fixed_amount".equals(type)) {
                double value = action.has("value") ? action.get("value").asDouble() : 0;
                return BigDecimal.valueOf(Math.min(Math.max(value, 0.0), subtotal));
            }

            if ("gift_dish".equals(type)) {
                // Mua X tặng Y: tính 100% giá của Y đơn vị
                int buyQty = action.has("buyQty") ? action.get("buyQty").asInt() : 2;
                int getQty = action.has("getQty") ? action.get("getQty").asInt() : 1;

                int sets = quantity / (buyQty + getQty);
                if (sets > 0) {
                    double unitPrice = subtotal / quantity;
                    return BigDecimal.valueOf(sets * getQty * unitPrice);
                }
            }

            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("Error calculating discount for promotion {}", promo.getId(), e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Kiểm tra KM còn lượt sử dụng không
     */
    public boolean isPromotionStillHasUsage(Promotion promo) {
        if (promo.getUsageLimit() == null) {
            return true; // Không giới hạn
        }
        return promo.getUsageCount() < promo.getUsageLimit();
    }

    /**
     * Tăng số lượt sử dụng (atomic)
     * @return true nếu tăng thành công, false nếu hết lượt
     */
    public boolean incrementUsageCount(Long promotionId) {
        int updated = promotionRepository.incrementUsageCount(promotionId);
        return updated > 0;
    }

    /**
     * Giảm số lượt sử dụng (hoàn lượt khi hủy)
     * @return true nếu giảm thành công
     */
    public boolean decrementUsageCount(Long promotionId) {
        int updated = promotionRepository.decrementUsageCount(promotionId);
        return updated > 0;
    }
}
