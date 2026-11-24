package com.group.restaurant.repositories;

import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.models.DiningTable;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {
    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DiningTable t where t.id = :id")
    Optional<DiningTable> findForGuestAdmission(@Param("id") Long id);

    List<DiningTable> findByBranchId(Long branchId);

    List<DiningTable> findByBranchIdAndStatus(Long branchId, DiningTableStatus status);

    List<DiningTable> findByAreaId(Long areaId);

    boolean existsByAreaId(Long areaId);

    @Query(
            value =
                    "select count(*) from dining_table where branch_id = :branchId and name = :name and is_deleted = true",
            nativeQuery = true)
    long countDeletedWithName(@Param("branchId") Long branchId, @Param("name") String name);

    Optional<DiningTable> findByBranchIdAndQrCode(Long branchId, String qrCode);

    long countByBranchId(Long branchId);

    @Query("SELECT t FROM DiningTable t WHERE t.branch.id = :branchId AND t.status = :status")
    List<DiningTable> findAvailableByBranchId(
            @Param("branchId") Long branchId, @Param("status") DiningTableStatus status);
}
