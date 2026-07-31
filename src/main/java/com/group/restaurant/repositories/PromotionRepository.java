package com.group.restaurant.repositories;

import com.group.restaurant.enums.PromotionStatus;
import com.group.restaurant.models.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findByRestaurantId(Long restaurantId);

    List<Promotion> findByRestaurantIdAndStatus(Long restaurantId, PromotionStatus status);

    Optional<Promotion> findByCode(String code);
}
