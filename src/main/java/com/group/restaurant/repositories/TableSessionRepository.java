package com.group.restaurant.repositories;

import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.models.TableSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableSessionRepository extends JpaRepository<TableSession, Long> {

    List<TableSession> findByTableId(Long tableId);

    List<TableSession> findByTableIdAndStatus(Long tableId, TableSessionStatus status);

    @Query("SELECT ts FROM TableSession ts WHERE ts.table.id = :tableId AND ts.status = :status")
    Optional<TableSession> findOpenSessionByTableId(@Param("tableId") Long tableId,
            @Param("status") TableSessionStatus status);

    @Query("SELECT ts FROM TableSession ts WHERE ts.table.branch.id = :branchId AND ts.status = :status")
    List<TableSession> findOpenSessionsByBranchId(@Param("branchId") Long branchId,
            @Param("status") TableSessionStatus status);

    long countByTableIdAndStatus(Long tableId, TableSessionStatus status);
}
