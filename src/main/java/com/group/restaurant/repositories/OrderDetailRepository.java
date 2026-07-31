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
}
