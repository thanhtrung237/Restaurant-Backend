package com.group.restaurant.repositories;

import com.group.restaurant.models.DishOptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DishOptionGroupRepository extends JpaRepository<DishOptionGroup, Long> {

    List<DishOptionGroup> findByDishId(Long dishId);

    List<DishOptionGroup> findByOptionGroupId(Long optionGroupId);

    // Native SQL để tìm cả liên kết đã xóa mềm, vốn bị @SQLRestriction ẩn đi.
    @Query(value = "SELECT * FROM dish_option_group WHERE dish_id = :dishId AND option_group_id = :groupId",
            nativeQuery = true)
    Optional<DishOptionGroup> findIncludingDeleted(
            @Param("dishId") Long dishId,
            @Param("groupId") Long optionGroupId);

    void deleteByDishId(Long dishId);

    void deleteByOptionGroupId(Long optionGroupId);
}
