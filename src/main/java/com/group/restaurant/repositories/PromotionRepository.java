package com.group.restaurant.repositories;

import com.group.restaurant.enums.PromotionStatus;
import com.group.restaurant.models.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findByRestaurantId(Long restaurantId);

    List<Promotion> findByRestaurantIdAndStatus(Long restaurantId, PromotionStatus status);

    Optional<Promotion> findByCode(String code);

    /**
     * Tìm khuyến mãi có cùng mã trong phạm vi một nhà hàng
     * (để kiểm tra trùng lặp, vì mã phải duy nhất trong một nhà hàng, không phải toàn hệ thống)
     */
    @Query("SELECT p FROM Promotion p WHERE p.restaurant.id = :restaurantId AND p.code = :code")
    Optional<Promotion> findByRestaurantIdAndCode(@Param("restaurantId") Long restaurantId, @Param("code") String code);
    
    /**
     * Tăng số lượt sử dụng (atomic operation để tránh race condition)
     * @return số dòng bị ảnh hưởng (1 nếu thành công, 0 nếu hết lượt)
     */
    @Modifying
    @Query("UPDATE Promotion p SET p.usageCount = p.usageCount + 1 " +
           "WHERE p.id = :promotionId " +
           "AND (p.usageLimit IS NULL OR p.usageCount < p.usageLimit)")
    int incrementUsageCount(@Param("promotionId") Long promotionId);
    
    /**
     * Giảm số lượt sử dụng (hoàn lượt khi hủy - atomic)
     * @return số dòng bị ảnh hưởng
     */
    @Modifying
    @Query("UPDATE Promotion p SET p.usageCount = p.usageCount - 1 " +
           "WHERE p.id = :promotionId AND p.usageCount > 0")
    int decrementUsageCount(@Param("promotionId") Long promotionId);
}
