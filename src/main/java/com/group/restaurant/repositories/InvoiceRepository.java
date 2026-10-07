package com.group.restaurant.repositories;

import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.models.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByTableSessionId(Long tableSessionId);

    List<Invoice> findByBranchId(Long branchId);

    List<Invoice> findByBranchIdAndStatus(Long branchId, InvoiceStatus status);

    @Query("SELECT i FROM Invoice i WHERE i.branch.id = :branchId AND i.createdAt BETWEEN :from AND :to")
    List<Invoice> findByBranchIdAndDateRange(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    boolean existsByInvoiceNumber(String invoiceNumber);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.branch.id = :branchId")
    long countByBranchId(@Param("branchId") Long branchId);

    // ═══════════════════════════════════════════════════════════════════
    // FR-RPT-01: Doanh thu theo khoảng thời gian + chi nhánh
    // Chỉ tính HĐ đã thu tiền: PAID(1) hoặc ISSUED(4)
    // ═══════════════════════════════════════════════════════════════════

    @Query("""
        SELECT COALESCE(SUM(i.grandTotal), 0)
        FROM Invoice i
        WHERE i.branch.id IN :branchIds
          AND i.status IN :statuses
          AND i.createdAt BETWEEN :from AND :to
        """)
    java.math.BigDecimal sumRevenue(
            @Param("branchIds")  List<Long> branchIds,
            @Param("statuses")   List<InvoiceStatus> statuses,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to);

    @Query("""
        SELECT COUNT(i)
        FROM Invoice i
        WHERE i.branch.id IN :branchIds
          AND i.status IN :statuses
          AND i.createdAt BETWEEN :from AND :to
        """)
    long countInvoices(
            @Param("branchIds")  List<Long> branchIds,
            @Param("statuses")   List<InvoiceStatus> statuses,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to);

    @Query("""
        SELECT COALESCE(SUM(ts.guestCount), 0)
        FROM Invoice i JOIN i.tableSession ts
        WHERE i.branch.id IN :branchIds
          AND i.status IN :statuses
          AND i.createdAt BETWEEN :from AND :to
        """)
    long sumGuests(
            @Param("branchIds")  List<Long> branchIds,
            @Param("statuses")   List<InvoiceStatus> statuses,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to);

    /** Doanh thu theo PTTT: trả về [methodName, totalAmount] */
    @Query("""
        SELECT p.method, COALESCE(SUM(p.amount), 0)
        FROM Payment p JOIN p.invoice i
        WHERE i.branch.id IN :branchIds
          AND i.status IN :statuses
          AND i.createdAt BETWEEN :from AND :to
        GROUP BY p.method
        """)
    List<Object[]> revenueByPaymentMethod(
            @Param("branchIds")  List<Long> branchIds,
            @Param("statuses")   List<InvoiceStatus> statuses,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to);

    /** Xu hướng doanh thu theo ngày: [date string, sum, count] */
    @Query(value = """
        SELECT DATE(i.created_at)  AS day,
               COALESCE(SUM(i.grand_total), 0) AS revenue,
               COUNT(i.id)         AS cnt
        FROM invoice i
        WHERE i.branch_id IN :branchIds
          AND i.status    IN :statuses
          AND i.created_at BETWEEN :from AND :to
          AND i.is_deleted = false
        GROUP BY DATE(i.created_at)
        ORDER BY day
        """, nativeQuery = true)
    List<Object[]> revenueTrendByDay(
            @Param("branchIds")  List<Long> branchIds,
            @Param("statuses")   List<Integer> statuses,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to);

    /** Xu hướng doanh thu theo tháng: [month string, sum, count] */
    @Query(value = """
        SELECT DATE_FORMAT(i.created_at, '%Y-%m') AS month,
               COALESCE(SUM(i.grand_total), 0)    AS revenue,
               COUNT(i.id)                         AS cnt
        FROM invoice i
        WHERE i.branch_id IN :branchIds
          AND i.status    IN :statuses
          AND i.created_at BETWEEN :from AND :to
          AND i.is_deleted = false
        GROUP BY month
        ORDER BY month
        """, nativeQuery = true)
    List<Object[]> revenueTrendByMonth(
            @Param("branchIds")  List<Long> branchIds,
            @Param("statuses")   List<Integer> statuses,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to);

    // ═══════════════════════════════════════════════════════════════════
    // FR-RPT-02: Doanh thu theo khung giờ trong ngày
    // ═══════════════════════════════════════════════════════════════════

    @Query(value = """
        SELECT HOUR(i.created_at)              AS hour,
               COALESCE(SUM(i.grand_total), 0) AS revenue,
               COUNT(DISTINCT i.table_session_id) AS tableCount
        FROM invoice i
        WHERE i.branch_id IN :branchIds
          AND i.status    IN :statuses
          AND i.created_at BETWEEN :from AND :to
          AND i.is_deleted = false
        GROUP BY HOUR(i.created_at)
        ORDER BY hour
        """, nativeQuery = true)
    List<Object[]> revenueByHour(
            @Param("branchIds")  List<Long> branchIds,
            @Param("statuses")   List<Integer> statuses,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to);

    @Query(value = """
        SELECT DAYOFWEEK(i.created_at)         AS dow,
               COALESCE(SUM(i.grand_total), 0) AS revenue,
               COUNT(i.id)                     AS cnt
        FROM invoice i
        WHERE i.branch_id IN :branchIds
          AND i.status    IN :statuses
          AND i.created_at BETWEEN :from AND :to
          AND i.is_deleted = false
        GROUP BY DAYOFWEEK(i.created_at)
        ORDER BY dow
        """, nativeQuery = true)
    List<Object[]> revenueByDayOfWeek(
            @Param("branchIds")  List<Long> branchIds,
            @Param("statuses")   List<Integer> statuses,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to);

    // ═══════════════════════════════════════════════════════════════════
    // FR-RPT-04: So sánh chi nhánh — tổng hợp theo từng branch
    // ═══════════════════════════════════════════════════════════════════

    @Query("""
        SELECT i.branch.id,
               COALESCE(SUM(i.grandTotal), 0),
               COUNT(i)
        FROM Invoice i
        WHERE i.branch.id IN :branchIds
          AND i.status IN :statuses
          AND i.createdAt BETWEEN :from AND :to
        GROUP BY i.branch.id
        """)
    List<Object[]> revenueGroupedByBranch(
            @Param("branchIds")  List<Long> branchIds,
            @Param("statuses")   List<InvoiceStatus> statuses,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to);
}
