package com.group.restaurant.repositories;

import com.group.restaurant.enums.AreaStatus;
import com.group.restaurant.models.Area;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AreaRepository extends JpaRepository<Area, Long> {

    List<Area> findByBranchId(Long branchId);

    List<Area> findByBranchIdAndStatus(Long branchId, AreaStatus status);

    boolean existsByBranchIdAndNameIgnoreCase(Long branchId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Area a where a.id = :id")
    Optional<Area> findForTableAssignment(@Param("id") Long id);

    @Query(value = "select id from area where branch_id = :branchId and name = :name and is_deleted = true", nativeQuery = true)
    Optional<Long> findDeletedId(@Param("branchId") Long branchId, @Param("name") String name);

    @Modifying
    @Query(value = "update area set status = :status, is_deleted = false, deleted_at = null, deleted_by = null where id = :id and is_deleted = true", nativeQuery = true)
    int restoreDeleted(@Param("id") Long id, @Param("status") int status);
}
