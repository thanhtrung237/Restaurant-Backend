package com.group.restaurant.repositories;

import com.group.restaurant.models.ManualDiscount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManualDiscountRepository extends JpaRepository<ManualDiscount, Long> {

    List<ManualDiscount> findByOrderId(Long orderId);

    List<ManualDiscount> findByInvoiceId(Long invoiceId);
}
