package com.group.restaurant.repositories;

import com.group.restaurant.enums.DishStatus;
import com.group.restaurant.models.Dish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DishRepository extends JpaRepository<Dish, Long> {

    List<Dish> findByRestaurantId(Long restaurantId);

    // Chỉ món gốc cấp nhà hàng (branch = null)
    List<Dish> findByRestaurantIdAndBranchIsNull(Long restaurantId);

    // Món riêng của 1 chi nhánh (FR-MNU-04)
    List<Dish> findByBranchId(Long branchId);

    List<Dish> findByCategoryId(Long categoryId);

    List<Dish> findByRestaurantIdAndStatus(Long restaurantId, DishStatus status);
}
