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
}
