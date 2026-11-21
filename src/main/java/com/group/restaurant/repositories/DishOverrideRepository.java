package com.group.restaurant.repositories;

import com.group.restaurant.models.DishOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DishOverrideRepository extends JpaRepository<DishOverride, Long> {

    List<DishOverride> findByBranchId(Long branchId);

    List<DishOverride> findByDishId(Long dishId);

    Optional<DishOverride> findByBranchIdAndDishId(Long branchId, Long dishId);

    // Bản ghi xóa mềm vẫn giữ khóa duy nhất (branch_id, dish_id).
    @Query(value = "SELECT * FROM dish_override WHERE branch_id = :branchId AND dish_id = :dishId",
            nativeQuery = true)
    Optional<DishOverride> findIncludingDeleted(@Param("branchId") Long branchId, @Param("dishId") Long dishId);
}
