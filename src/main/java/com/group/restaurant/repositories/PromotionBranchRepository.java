package com.group.restaurant.repositories;

import com.group.restaurant.models.PromotionBranch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionBranchRepository extends JpaRepository<PromotionBranch, Long> {

    List<PromotionBranch> findByBranchId(Long branchId);

    List<PromotionBranch> findByPromotionId(Long promotionId);

    boolean existsByBranchIdAndPromotionId(Long branchId, Long promotionId);

    @Modifying
    @Query(
            value = "DELETE FROM promotion_branch WHERE promotion_id = :promotionId",
            nativeQuery = true)
    void hardDeleteByPromotionId(@Param("promotionId") Long promotionId);

    @Modifying
    @Query(value = "DELETE FROM promotion_branch WHERE id = :id", nativeQuery = true)
    void hardDeleteById(@Param("id") Long id);

    @Modifying
    @Query(
            value =
                    "DELETE FROM promotion_branch WHERE branch_id = :branchId AND promotion_id = :promotionId",
            nativeQuery = true)
    void hardDeleteByBranchIdAndPromotionId(
            @Param("branchId") Long branchId, @Param("promotionId") Long promotionId);
}
