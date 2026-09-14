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

    // 1. Tìm phiên hoạt động duy nhất của bàn (Trừ CLOSED và CANCELLED)
    @Query("SELECT ts FROM TableSession ts WHERE ts.table.id = :tableId AND ts.status NOT IN (com.group.restaurant.enums.TableSessionStatus.CLOSED, com.group.restaurant.enums.TableSessionStatus.CANCELLED)")
    Optional<TableSession> findActiveSessionByTableId(@Param("tableId") Long tableId);

    // 2. Lấy danh sách phiên hoạt động theo chi nhánh
    @Query("SELECT ts FROM TableSession ts WHERE ts.table.branch.id = :branchId AND ts.status NOT IN (com.group.restaurant.enums.TableSessionStatus.CLOSED, com.group.restaurant.enums.TableSessionStatus.CANCELLED)")
    List<TableSession> findActiveSessionsByBranchId(@Param("branchId") Long branchId);

    // 3. Đếm số phiên đang hoạt động của bàn (Trả về primitive long để tránh Null
    // Safety Warning)
    @Query("SELECT COUNT(ts) FROM TableSession ts WHERE ts.table.id = :tableId AND ts.status NOT IN (com.group.restaurant.enums.TableSessionStatus.CLOSED, com.group.restaurant.enums.TableSessionStatus.CANCELLED)")
    long countActiveSessionsByTableId(@Param("tableId") Long tableId);

    // 4. BỔ SUNG: Tìm phiên đang hoạt động theo tableId VÀ status cụ thể (Giải
    // quyết lỗi cancelReservation)
    @Query("SELECT ts FROM TableSession ts WHERE ts.table.id = :tableId AND ts.status = :status ORDER BY ts.id DESC LIMIT 1")
    Optional<TableSession> findActiveSessionByTableIdAndStatus(
            @Param("tableId") Long tableId,
            @Param("status") TableSessionStatus status);

    // 5. Tìm phiên mở theo bàn và trạng thái
    @Query("SELECT ts FROM TableSession ts WHERE ts.table.id = :tableId AND ts.status = :status")
    Optional<TableSession> findOpenSessionByTableId(
            @Param("tableId") Long tableId,
            @Param("status") TableSessionStatus status);

    // 6. Lấy danh sách phiên mở theo chi nhánh
    @Query("SELECT ts FROM TableSession ts WHERE ts.table.branch.id = :branchId AND ts.status = :status")
    List<TableSession> findOpenSessionsByBranchId(
            @Param("branchId") Long branchId,
            @Param("status") TableSessionStatus status);

    long countByTableIdAndStatus(Long tableId, TableSessionStatus status);
}