package com.group.restaurant.repositories;

import com.group.restaurant.enums.CategoryStatus;
import com.group.restaurant.enums.DishStatus;
import com.group.restaurant.models.Dish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // ------------------------------------------------------------------------------
    // FR-MNU-04 — thực đơn hiển thị: chỉ món ĐANG BÁN thuộc danh mục ĐANG HIỆN.
    // Đẩy điều kiện xuống truy vấn thay vì lọc trong bộ nhớ.
    // ------------------------------------------------------------------------------

    @Query("SELECT d FROM Dish d JOIN d.category c " +
           "WHERE d.restaurant.id = :restaurantId AND d.branch IS NULL " +
           "AND d.status = :dishStatus AND c.status = :categoryStatus")
    List<Dish> findActiveRestaurantDishes(
            @Param("restaurantId") Long restaurantId,
            @Param("dishStatus") DishStatus dishStatus,
            @Param("categoryStatus") CategoryStatus categoryStatus);

    @Query("SELECT d FROM Dish d JOIN d.category c " +
           "WHERE d.branch.id = :branchId " +
           "AND d.status = :dishStatus AND c.status = :categoryStatus")
    List<Dish> findActiveBranchDishes(
            @Param("branchId") Long branchId,
            @Param("dishStatus") DishStatus dishStatus,
            @Param("categoryStatus") CategoryStatus categoryStatus);
}
