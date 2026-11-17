package com.group.restaurant.repositories;

import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.models.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {

    List<OrderDetail> findByOrderId(Long orderId);

    List<OrderDetail> findByOrderIdAndStatus(Long orderId, OrderDetailStatus status);

    @Query("SELECT od FROM OrderDetail od WHERE od.order.tableSession.id = :sessionId")
    List<OrderDetail> findByTableSessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT od FROM OrderDetail od WHERE od.order.tableSession.id = :sessionId AND od.status = :status")
    List<OrderDetail> findByTableSessionIdAndStatus(
            @Param("sessionId") Long sessionId,
            @Param("status") OrderDetailStatus status);

    long countByOrderId(Long orderId);

    /**
     * FR-MNU-02 — món đã nằm trên một hoá đơn bất kỳ (mọi trạng thái, mọi chi nhánh).
     * Dùng để chặn xoá món; @SQLRestriction đã loại bản ghi đã xoá mềm.
     */
    @Query("SELECT COUNT(od) > 0 FROM OrderDetail od " +
           "WHERE od.dish.id = :dishId " +
           "AND EXISTS (SELECT 1 FROM Invoice i WHERE i.tableSession.id = od.order.tableSession.id)")
    boolean existsOnAnyInvoice(@Param("dishId") Long dishId);

    // ═══════════════════════════════════════════════════════════════════
    // FR-RPT-03: Món bán chạy / bán chậm
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Tổng số lượng và doanh thu từng món trong khoảng thời gian + chi nhánh.
     * Chỉ tính dòng đã SERVED (status=2) và is_billable=true.
     * PENDING/PREPARING không tính vì chưa thực sự "bán" được (FR-RPT-03).
     * Trả về: [dishId, dishName, categoryName, totalQty, totalRevenue]
     */
    @Query(value = """
        SELECT od.dish_id,
               d.name                              AS dishName,
               c.name                              AS categoryName,
               COALESCE(SUM(od.quantity), 0)       AS totalQty,
               COALESCE(SUM(od.price), 0)          AS totalRevenue
        FROM order_detail od
        JOIN orders o      ON o.id = od.order_id
        JOIN table_session ts ON ts.id = od.table_session_id
        JOIN dish d        ON d.id = od.dish_id
        LEFT JOIN category c ON c.id = d.category_id
        WHERE ts.branch_id IN :branchIds
          AND od.is_billable = true
          AND od.status      IN (2, 3)
          AND od.is_deleted  = false
          AND o.is_deleted   = false
          AND ts.created_at  BETWEEN :from AND :to
        GROUP BY od.dish_id, d.name, c.name
        ORDER BY totalRevenue DESC
        """, nativeQuery = true)
    List<Object[]> dishSalesSummary(
            @Param("branchIds") List<Long> branchIds,
            @Param("from")      java.time.LocalDateTime from,
            @Param("to")        java.time.LocalDateTime to);

    // ═══════════════════════════════════════════════════════════════════
    // FR-RPT-04: Tỷ lệ hủy món tách theo lý do (BR-24)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Đếm dòng bị hủy tách theo cancel_reason và branch.
     * Trả về: [branchId, cancelReason (int), count]
     */
    @Query(value = """
        SELECT ts.branch_id,
               od.cancel_reason,
               COUNT(od.id) AS cnt
        FROM order_detail od
        JOIN orders o      ON o.id  = od.order_id
        JOIN table_session ts ON ts.id = od.table_session_id
        WHERE od.status    = 3
          AND ts.branch_id IN :branchIds
          AND ts.created_at BETWEEN :from AND :to
          AND od.is_deleted = false
          AND o.is_deleted  = false
        GROUP BY ts.branch_id, od.cancel_reason
        """, nativeQuery = true)
    List<Object[]> cancelCountByBranchAndReason(
            @Param("branchIds") List<Long> branchIds,
            @Param("from")      java.time.LocalDateTime from,
            @Param("to")        java.time.LocalDateTime to);

    /**
     * AI Anomaly: Tìm các phiên bàn có từ 2 lần hủy món trở lên.
     * Trả về: [sessionId, branchId, tableName, cancelCount, cancelAmount]
     */
    @Query(value = """
        SELECT ts.id AS sessionId,
               ts.branch_id AS branchId,
               COALESCE(dt.name, CONCAT('Bàn ', ts.table_id)) AS tableName,
               COUNT(od.id) AS cancelCount,
               COALESCE(SUM(od.price_at_order_time * od.quantity), 0) AS cancelAmount
        FROM order_detail od
        JOIN table_session ts ON ts.id = od.table_session_id
        LEFT JOIN dining_table dt ON dt.id = ts.table_id
        WHERE ts.branch_id IN :branchIds
          AND od.status = 3
          AND od.is_deleted = false
          AND ts.created_at BETWEEN :from AND :to
        GROUP BY ts.id, ts.branch_id, dt.name, ts.table_id
        HAVING COUNT(od.id) >= 2
        ORDER BY cancelCount DESC
        LIMIT 10
        """, nativeQuery = true)
    List<Object[]> findSessionsWithMultipleCancellations(
            @Param("branchIds") List<Long> branchIds,
            @Param("from")      java.time.LocalDateTime from,
            @Param("to")        java.time.LocalDateTime to);

    /**
     * AI Anomaly: Tìm khung giờ có đột biến hủy món (>= 2 món trong 1 khung giờ).
     * Trả về: [branchId, hour, cancelCount]
     */
    @Query(value = """
        SELECT ts.branch_id AS branchId,
               HOUR(od.created_at) AS hr,
               COUNT(od.id) AS cancelCount
        FROM order_detail od
        JOIN table_session ts ON ts.id = od.table_session_id
        WHERE ts.branch_id IN :branchIds
          AND od.status = 3
          AND od.is_deleted = false
          AND ts.created_at BETWEEN :from AND :to
        GROUP BY ts.branch_id, HOUR(od.created_at)
        HAVING COUNT(od.id) >= 2
        ORDER BY cancelCount DESC
        LIMIT 5
        """, nativeQuery = true)
    List<Object[]> findHourlyCancellationSpikes(
            @Param("branchIds") List<Long> branchIds,
            @Param("from")      java.time.LocalDateTime from,
            @Param("to")        java.time.LocalDateTime to);

    /** Tổng dòng món tính tiền (SERVED + CANCELLED billable) theo chi nhánh — làm mẫu số tỷ lệ hủy.
     *  Loại trừ PENDING_WEIGHT(4) vì chưa có giá chính thức.
     */
    @Query(value = """
        SELECT ts.branch_id, COUNT(od.id) AS total
        FROM order_detail od
        JOIN orders o      ON o.id  = od.order_id
        JOIN table_session ts ON ts.id = od.table_session_id
        WHERE ts.branch_id IN :branchIds
          AND ts.created_at BETWEEN :from AND :to
          AND od.status     != 4
          AND od.is_deleted = false
          AND o.is_deleted  = false
        GROUP BY ts.branch_id
        """, nativeQuery = true)
    List<Object[]> totalDetailCountByBranch(
            @Param("branchIds") List<Long> branchIds,
            @Param("from")      java.time.LocalDateTime from,
            @Param("to")        java.time.LocalDateTime to);
}
