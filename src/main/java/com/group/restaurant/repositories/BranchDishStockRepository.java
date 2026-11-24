package com.group.restaurant.repositories;

import com.group.restaurant.enums.StockStatus;
import com.group.restaurant.models.BranchDishStock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BranchDishStockRepository extends JpaRepository<BranchDishStock, Long> {

    Optional<BranchDishStock> findByBranchIdAndDishId(Long branchId, Long dishId);

    List<BranchDishStock> findByBranchId(Long branchId);

    List<BranchDishStock> findByBranchIdAndStatus(Long branchId, StockStatus status);

    @Modifying
    @Query(
            "UPDATE BranchDishStock s SET s.status = com.group.restaurant.enums.StockStatus.AVAILABLE WHERE s.branch.id = :branchId AND s.status = com.group.restaurant.enums.StockStatus.OUT_OF_STOCK")
    int resetStockForBranch(@Param("branchId") Long branchId);
}
