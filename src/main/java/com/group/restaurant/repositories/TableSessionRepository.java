package com.group.restaurant.repositories;

import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.models.TableSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TableSessionRepository extends JpaRepository<TableSession, Long> {
    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select s from TableSession s where s.table.id = :tableId and s.status not in (com.group.restaurant.enums.TableSessionStatus.CLOSED, com.group.restaurant.enums.TableSessionStatus.CANCELLED, com.group.restaurant.enums.TableSessionStatus.MERGED)")
    Optional<TableSession> findActiveForGuestAdmission(@Param("tableId") Long tableId);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TableSession s where s.id = :id")
    Optional<TableSession> findForUpdate(@Param("id") Long id);

    List<TableSession> findByTableId(Long tableId);

    @Query(
            value = "select count(*) from table_session where table_id = :tableId",
            nativeQuery = true)
    long countHistoryByTableId(@Param("tableId") Long tableId);

    List<TableSession> findByTableIdAndStatus(Long tableId, TableSessionStatus status);

    // Tìm phiên hoạt động duy nhất của bàn (Trừ CLOSED, CANCELLED, MERGED)
    @Query(
            "SELECT ts FROM TableSession ts WHERE ts.table.id = :tableId AND ts.status NOT IN (com.group.restaurant.enums.TableSessionStatus.CLOSED, com.group.restaurant.enums.TableSessionStatus.CANCELLED, com.group.restaurant.enums.TableSessionStatus.MERGED)")
    Optional<TableSession> findActiveSessionByTableId(@Param("tableId") Long tableId);

    // Lấy danh sách phiên hoạt động theo chi nhánh
    @Query(
            """
                SELECT ts FROM TableSession ts WHERE ts.branch.id = :branchId
                  AND ts.status NOT IN (com.group.restaurant.enums.TableSessionStatus.CANCELLED, com.group.restaurant.enums.TableSessionStatus.MERGED)
                  AND ((ts.table IS NOT NULL AND ts.status <> com.group.restaurant.enums.TableSessionStatus.CLOSED)
                       OR (ts.table IS NULL AND ts.pickedUpAt IS NULL))
                """)
    List<TableSession> findActiveSessionsByBranchId(@Param("branchId") Long branchId);

    List<TableSession> findByBranchIdAndTableIsNullAndPickedUpAtIsNullAndStatusNotOrderByIdAsc(
            Long branchId, TableSessionStatus status);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TableSession s where s.id = :id and s.table is null")
    Optional<TableSession> findTakeawayForUpdate(Long id);

    // Đếm số phiên đang hoạt động của bàn
    @Query(
            "SELECT COUNT(ts) FROM TableSession ts WHERE ts.table.id = :tableId AND ts.status NOT IN (com.group.restaurant.enums.TableSessionStatus.CLOSED, com.group.restaurant.enums.TableSessionStatus.CANCELLED, com.group.restaurant.enums.TableSessionStatus.MERGED)")
    long countActiveSessionsByTableId(@Param("tableId") Long tableId);

    // Tìm phiên đang hoạt động theo tableId VÀ status cụ thể (Giải quyết lỗi cancelReservation)
    @Query(
            "SELECT ts FROM TableSession ts WHERE ts.table.id = :tableId AND ts.status = :status ORDER BY ts.id DESC LIMIT 1")
    Optional<TableSession> findActiveSessionByTableIdAndStatus(
            @Param("tableId") Long tableId, @Param("status") TableSessionStatus status);

    // Tìm phiên mở theo bàn và trạng thái
    @Query("SELECT ts FROM TableSession ts WHERE ts.table.id = :tableId AND ts.status = :status")
    Optional<TableSession> findOpenSessionByTableId(
            @Param("tableId") Long tableId, @Param("status") TableSessionStatus status);

    // Lấy danh sách phiên mở theo chi nhánh
    @Query(
            "SELECT ts FROM TableSession ts WHERE ts.table.branch.id = :branchId AND ts.status = :status")
    List<TableSession> findOpenSessionsByBranchId(
            @Param("branchId") Long branchId, @Param("status") TableSessionStatus status);

    long countByTableIdAndStatus(Long tableId, TableSessionStatus status);

    List<TableSession> findByStatus(TableSessionStatus status);
}
