package com.group.restaurant.repositories;

import com.group.restaurant.enums.OrderStatus;
import com.group.restaurant.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByTableSessionId(Long tableSessionId);

    List<Order> findByTableSessionIdAndStatus(Long tableSessionId, OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.tableSession.branch.id = :branchId AND o.status = :status")
    List<Order> findByBranchIdAndStatus(@Param("branchId") Long branchId, @Param("status") OrderStatus status);

    long countByTableSessionId(Long tableSessionId);
}
