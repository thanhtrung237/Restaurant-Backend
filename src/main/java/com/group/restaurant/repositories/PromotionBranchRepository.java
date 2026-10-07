package com.group.restaurant.repositories;

import com.group.restaurant.models.PromotionBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromotionBranchRepository extends JpaRepository<PromotionBranch, Long> {

    List<PromotionBranch> findByBranchId(Long branchId);

    List<PromotionBranch> findByPromotionId(Long promotionId);

    boolean existsByBranchIdAndPromotionId(Long branchId, Long promotionId);
}
