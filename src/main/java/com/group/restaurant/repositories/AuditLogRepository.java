package com.group.restaurant.repositories;

import com.group.restaurant.models.AuditLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(
            "SELECT a FROM AuditLog a WHERE a.restaurant.id = :restaurantId "
                    + "ORDER BY a.occurredAt DESC")
    List<AuditLog> findRecentByRestaurantId(
            @Param("restaurantId") Long restaurantId, Pageable pageable);

    // ------------------------------------------------------------------------------
    // Biến thể trả LIST, mới nhất trước — dùng cho 4 endpoint không phân trang
    // (/restaurant/{id}, /branch/{id}, /entity-type/{t}, /performed-by/{id}).
    //
    // Trước đây chúng gọi cùng phương thức trả Page kèm Pageable.unpaged(), và hai
    // đường /restaurant/{id} + /performed-by/{id} trả RỖNG dù có dữ liệu
    // (be-issues mục 16). Trả List trực tiếp thì không còn phụ thuộc vào cách
    // Spring Data xử lý Pageable.unpaged(), và có luôn thứ tự mới-nhất-trước mà
    // màn Nhật ký cần.
    // ------------------------------------------------------------------------------

    List<AuditLog> findByRestaurantIdOrderByOccurredAtDesc(Long restaurantId);

    List<AuditLog> findByRestaurantIdAndBranchIdInOrderByOccurredAtDesc(
            Long restaurantId, List<Long> branchIds);

    List<AuditLog> findByBranchIdOrderByOccurredAtDesc(Long branchId);

    List<AuditLog> findByEntityTypeOrderByOccurredAtDesc(String entityType);

    List<AuditLog> findByEntityTypeAndBranchIdInOrderByOccurredAtDesc(
            String entityType, List<Long> branchIds);

    List<AuditLog> findByPerformedByIdOrderByOccurredAtDesc(Long performedById);

    List<AuditLog> findByPerformedByIdAndBranchIdInOrderByOccurredAtDesc(
            Long performedById, List<Long> branchIds);

    // ------------------------------------------------------------------------------
    // BR-18 — giới hạn theo DANH SÁCH chi nhánh được phân công.
    //
    // Dùng cho người có vai trò phạm vi CHI NHÁNH: họ chỉ được thấy nhật ký của chi nhánh
    // mình, kể cả khi lời gọi không truyền branchId.
    //
    // Phạm vi gồm HAI nhóm (be-issues mục 21):
    //   1. Dòng gắn đúng chi nhánh đó.
    //   2. Dòng ĐĂNG NHẬP / ĐĂNG XUẤT của tài khoản CÓ PHÂN CÔNG ở chi nhánh đó.
    //
    // Vì sao cần nhóm 2: đăng nhập là việc của một CON NGƯỜI, không thuộc chi nhánh nào,
    // nên `AuthServiceImpl` ghi log không kèm branchId. Nếu chỉ lọc `branch.id IN (...)`
    // thì QL chi nhánh không bao giờ thấy được vết đăng nhập của nhân viên mình — trái
    // FR-ADM-04 và Bảng 4.2 ("Xem nhật ký hoạt động chi nhánh mình").
    //
    // Các sự kiện cấp nhà hàng KHÁC (đổi vai trò, đổi cấu hình nhà hàng) vẫn bị loại —
    // đúng ý BR-18 "chỉ nhật ký chi nhánh mình".
    //
    // Dòng đăng nhập THẤT BẠI với tên đăng nhập không tồn tại có `performedBy = null`,
    // không quy được về chi nhánh nào, nên chỉ Quản trị nhà hàng thấy.
    // ------------------------------------------------------------------------------

    String BRANCH_SCOPE =
            "(a.branch.id IN :branchIds "
                    + " OR (a.actionType IN ('LOGIN', 'LOGOUT') AND EXISTS ("
                    + "      SELECT 1 FROM Assignment asg"
                    + "      WHERE asg.account = a.performedBy AND asg.branch.id IN :branchIds"
                    // QĐ-11: chỉ phân công CẤP CHI NHÁNH. Vai trò cấp nhà hàng không gắn chi nhánh
                    // (Bảng thuật ngữ), nên đăng nhập của Quản trị nhà hàng KHÔNG thuộc nhật ký chi
                    // nhánh — dù seed có gán branch_id cho phân công MANAGER.
                    + "        AND asg.role.scope = com.group.restaurant.enums.RoleScope.BRANCH)))";

    /**
     * Lọc theo loại thao tác (be-issues mục 22, FR-LOG-01 "loại hành động").
     *
     * Dùng cờ `:filterByAction` thay vì so `:actionTypes IS NULL`: JPQL không nhận tham số
     * danh sách rỗng/null trong `IN`. Khi không lọc, bên gọi truyền cờ false và một danh sách
     * bất kỳ khác rỗng.
     */
    String ACTION_FILTER = " AND (:filterByAction = FALSE OR a.actionType IN :actionTypes) ";

    /**
     * Truy vấn dùng chung cho `GET /audit-logs` — gom mọi bộ lọc tuỳ chọn vào MỘT câu.
     *
     * Trước đây service rẽ 6 nhánh theo tổ hợp tham số; thêm một bộ lọc nữa là phải nhân đôi
     * số nhánh, và rất dễ quên áp chốt BR-18 ở một nhánh nào đó. Mỗi bộ lọc ở đây đi kèm một
     * cờ bật/tắt nên nhánh nào cũng đi qua đúng cùng một điều kiện phạm vi.
     */
    @Query(
            "SELECT a FROM AuditLog a WHERE "
                    + "(:restaurantId IS NULL OR a.restaurant.id = :restaurantId) "
                    + "AND (:entityType IS NULL OR a.entityType = :entityType) "
                    + "AND (:filterByDate = FALSE OR a.occurredAt BETWEEN :startDate AND :endDate) "
                    + "AND (:filterByBranch = FALSE OR "
                    + BRANCH_SCOPE
                    + ")"
                    + ACTION_FILTER)
    Page<AuditLog> search(
            @Param("restaurantId") Long restaurantId,
            @Param("entityType") String entityType,
            @Param("filterByDate") boolean filterByDate,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("filterByBranch") boolean filterByBranch,
            @Param("branchIds") List<Long> branchIds,
            @Param("filterByAction") boolean filterByAction,
            @Param("actionTypes") List<String> actionTypes,
            Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " + BRANCH_SCOPE + " ORDER BY a.occurredAt DESC")
    List<AuditLog> findBranchScopedList(@Param("branchIds") List<Long> branchIds);

    @Query(
            "SELECT a FROM AuditLog a WHERE a.restaurant.id = :restaurantId AND "
                    + BRANCH_SCOPE
                    + " ORDER BY a.occurredAt DESC")
    List<AuditLog> findBranchScopedByRestaurantList(
            @Param("restaurantId") Long restaurantId, @Param("branchIds") List<Long> branchIds);

    @Query(
            "SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND "
                    + BRANCH_SCOPE
                    + " ORDER BY a.occurredAt DESC")
    List<AuditLog> findBranchScopedByEntityTypeList(
            @Param("entityType") String entityType, @Param("branchIds") List<Long> branchIds);

    @Query(
            "SELECT a FROM AuditLog a WHERE a.performedBy.id = :performedById AND "
                    + BRANCH_SCOPE
                    + " ORDER BY a.occurredAt DESC")
    List<AuditLog> findBranchScopedByPerformedByList(
            @Param("performedById") Long performedById, @Param("branchIds") List<Long> branchIds);
}
