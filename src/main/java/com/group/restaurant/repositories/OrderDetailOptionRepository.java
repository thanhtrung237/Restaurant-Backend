package com.group.restaurant.repositories;

import com.group.restaurant.models.OrderDetailOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderDetailOptionRepository extends JpaRepository<OrderDetailOption, Long> {

    List<OrderDetailOption> findByOrderDetailId(Long orderDetailId);
}
