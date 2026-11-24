package com.group.restaurant.repositories;

import com.group.restaurant.enums.AccountStatus;
import com.group.restaurant.models.Assignment;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @Query(
            "select count(distinct a.account.id) from Assignment a where a.account.restaurant.id = :restaurantId "
                    + "and a.role.code = :roleCode and a.branch is not null and a.account.status = :status")
    long countAccountsForPermissions(Long restaurantId, String roleCode, AccountStatus status);

    List<Assignment> findByAccountId(Long accountId);

    List<Assignment> findByRoleId(Long roleId);

    List<Assignment> findByBranchId(Long branchId);

    @EntityGraph(attributePaths = {"account", "role", "branch"})
    @Query(
            "select a from Assignment a where a.account.id in "
                    + "(select member.account.id from Assignment member where member.branch.id = :branchId)")
    List<Assignment> findForBranchStaff(Long branchId);

    void deleteByAccountId(Long accountId);

    void deleteByRoleId(Long roleId);

    // ------------------------------------------------------------------------------
    // Chặn tự khoá mình ra khỏi hệ thống: phải luôn còn ít nhất MỘT Quản trị nhà hàng
    // đang hoạt động. Hai truy vấn dưới đây đếm số tài khoản KHÁC còn giữ vai trò đó.
    // ------------------------------------------------------------------------------

    /** Đếm tài khoản khác (trừ {@code excludeAccountId}) đang ACTIVE và giữ vai trò này. */
    @Query(
            "SELECT COUNT(DISTINCT a.account.id) FROM Assignment a "
                    + "WHERE a.role.code = :roleCode "
                    + "AND a.account.restaurant.id = :restaurantId "
                    + "AND a.account.status = :status "
                    + "AND a.account.id <> :excludeAccountId")
    long countActiveAccountsWithRoleExcludingAccount(
            @Param("roleCode") String roleCode,
            @Param("restaurantId") Long restaurantId,
            @Param("status") AccountStatus status,
            @Param("excludeAccountId") Long excludeAccountId);

    /** Đếm tài khoản đang ACTIVE giữ vai trò này qua một phân công KHÁC phân công đang xét. */
    @Query(
            "SELECT COUNT(DISTINCT a.account.id) FROM Assignment a "
                    + "WHERE a.role.code = :roleCode "
                    + "AND a.account.restaurant.id = :restaurantId "
                    + "AND a.account.status = :status "
                    + "AND a.id <> :excludeAssignmentId")
    long countActiveAccountsWithRoleExcludingAssignment(
            @Param("roleCode") String roleCode,
            @Param("restaurantId") Long restaurantId,
            @Param("status") AccountStatus status,
            @Param("excludeAssignmentId") Long excludeAssignmentId);
}
