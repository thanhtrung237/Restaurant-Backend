package com.group.restaurant.repositories;

import com.group.restaurant.models.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByRestaurantId(Long restaurantId, Pageable pageable);

    Page<AuditLog> findByBranchId(Long branchId, Pageable pageable);

    Page<AuditLog> findByPerformedById(Long performedById, Pageable pageable);

    Page<AuditLog> findByEntityType(String entityType, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.restaurant.id = :restaurantId " +
           "AND a.occurredAt BETWEEN :start AND :end " +
           "ORDER BY a.occurredAt DESC")
    Page<AuditLog> findByRestaurantIdAndDateRange(
        @Param("restaurantId") Long restaurantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        Pageable pageable
    );

    @Query("SELECT a FROM AuditLog a WHERE a.restaurant.id = :restaurantId " +
           "ORDER BY a.occurredAt DESC")
    List<AuditLog> findRecentByRestaurantId(@Param("restaurantId") Long restaurantId, Pageable pageable);
}
