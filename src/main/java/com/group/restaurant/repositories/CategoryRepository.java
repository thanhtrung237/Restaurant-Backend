package com.group.restaurant.repositories;

import com.group.restaurant.enums.CategoryStatus;
import com.group.restaurant.models.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByRestaurantIdOrderByDisplayOrderAsc(Long restaurantId);

    List<Category> findByRestaurantIdAndStatus(Long restaurantId, CategoryStatus status);
}
