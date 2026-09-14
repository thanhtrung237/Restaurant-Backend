package com.group.restaurant.repositories;

import com.group.restaurant.enums.BranchStatus;
import com.group.restaurant.models.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByRestaurantId(Long restaurantId);

    Optional<Branch> findByRestaurantIdAndCode(Long restaurantId, String code);

    long countByRestaurantId(Long restaurantId);

    @Query("SELECT b FROM Branch b WHERE b.restaurant.id = :restaurantId AND b.status = :status")
    List<Branch> findByRestaurantIdAndStatus(@Param("restaurantId") Long restaurantId,
            @Param("status") BranchStatus status);

    long countByRestaurantIdAndStatus(Long restaurantId, BranchStatus status);
}
