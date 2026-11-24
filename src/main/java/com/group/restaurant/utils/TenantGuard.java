package com.group.restaurant.utils;

import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.Assignment;
import com.group.restaurant.models.Branch;
import com.group.restaurant.repositories.BranchRepository;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Chốt cách ly dữ liệu giữa các nhà hàng (multi-tenant).
 *
 * VẤN ĐỀ: mọi service nhận `branchId` / `restaurantId` trực tiếp từ path hoặc body rồi
 * tin luôn, không đối chiếu với nhà hàng của người đang đăng nhập. Nhân viên nhà hàng A
 * đăng nhập hợp lệ rồi gọi `GET /invoices/branch/3` là đọc được doanh thu nhà hàng B —
 * IDOR xuyên tenant trên toàn bộ endpoint. {@code SecurityUtils.getCurrentRestaurantId()}
 * đã tồn tại nhưng KHÔNG service nào gọi.
 *
 * HỢP ĐỒNG QUAN TRỌNG — vì sao guard "im lặng bỏ qua" khi không có ai đăng nhập:
 * luồng khách quét QR (tạo phiên bàn, gửi order, xem hóa đơn) là ẩn danh theo thiết kế
 * (xem SecurityConfig.PUBLIC_GET/PUBLIC_POST). Nếu guard ném lỗi khi không có
 * Authentication thì toàn bộ luồng khách vỡ. Cách ly cho luồng khách phải giải bằng
 * token phiên bàn — việc còn tồn đọng, KHÔNG phải nhiệm vụ của class này.
 *
 * Vai trò cấp PLATFORM được bỏ qua kiểm tra: Quản trị nền tảng vận hành xuyên nhà hàng.
 */
@Component
@RequiredArgsConstructor
public class TenantGuard {

    private final BranchRepository branchRepository;

    /**
     * Nhà hàng của người đang đăng nhập, hoặc null khi request là ẩn danh (luồng khách).
     * Trả null thay vì ném lỗi để chỗ gọi dùng được ở cả endpoint công khai.
     */
    public Long currentRestaurantIdOrNull() {
        AccountUserDetails details = currentUserOrNull();
        if (details != null && details.getRestaurantId() == null && !isPlatformAdmin()) {
            throw new AccessDeniedException(
                    "Tài khoản chưa được gán nhà hàng hoặc quyền nền tảng hợp lệ.");
        }
        return details != null ? details.getRestaurantId() : null;
    }

    /** true nếu đang đăng nhập bằng tài khoản có vai trò cấp nền tảng. */
    public boolean isPlatformAdmin() {
        AccountUserDetails details = currentUserOrNull();
        if (details == null) {
            return false;
        }
        Account account = details.getAccount();
        return account.getAssignments() != null
                && account.getAssignments().stream()
                        .map(Assignment::getRole)
                        .anyMatch(
                                role ->
                                        role != null
                                                && (role.getScope() == RoleScope.PLATFORM
                                                        || "PLATFORM_ADMIN"
                                                                .equals(role.getCode())));
    }

    /**
     * Chặn nếu `restaurantId` không phải nhà hàng của người đang đăng nhập.
     * No-op khi ẩn danh hoặc khi là Quản trị nền tảng.
     */
    public void assertRestaurant(Long restaurantId) {
        Long current = currentRestaurantIdOrNull();
        if (current == null || isPlatformAdmin() || restaurantId == null) {
            return;
        }
        if (!current.equals(restaurantId)) {
            throw new AccessDeniedException("Không có quyền truy cập dữ liệu của nhà hàng khác");
        }
    }

    /**
     * Chặn nếu chi nhánh không thuộc nhà hàng của người đang đăng nhập.
     * No-op khi ẩn danh hoặc khi là Quản trị nền tảng.
     */
    public void assertBranch(Long branchId) {
        Long current = currentRestaurantIdOrNull();
        if (current == null || isPlatformAdmin() || branchId == null) {
            return;
        }
        Branch branch =
                branchRepository
                        .findById(branchId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy chi nhánh với ID: " + branchId));
        assertBranch(branch);
    }

    /** Biến thể nhận sẵn entity, tránh query lại khi chỗ gọi đã load chi nhánh. */
    public void assertBranch(Branch branch) {
        Long current = currentRestaurantIdOrNull();
        if (current == null || isPlatformAdmin() || branch == null) {
            return;
        }
        Long ownerRestaurantId =
                branch.getRestaurant() != null ? branch.getRestaurant().getId() : null;
        if (ownerRestaurantId == null || !current.equals(ownerRestaurantId)) {
            throw new AccessDeniedException("Chi nhánh không thuộc nhà hàng của bạn");
        }
    }

    // ==================================================================================
    // BR-18 — cách ly theo CHI NHÁNH ĐƯỢC PHÂN CÔNG.
    //
    // Khác assertBranch(): assertBranch chỉ hỏi "chi nhánh này có thuộc nhà hàng của bạn
    // không". Nhóm hàm dưới đây hỏi thêm "bạn có được phân công ở chi nhánh đó không, VÀ
    // với vai trò nào". assertBranch/assertRestaurant giữ nguyên, áp dần từng endpoint.
    // ==================================================================================

    /** Mã vai trò, khớp cột `role.code` trong DB và phần sau "ROLE_" của authority. */
    public static final String ROLE_MANAGER = "MANAGER";

    public static final String ROLE_BRANCH_MANAGER = "BRANCH_MANAGER";
    public static final String ROLE_WAITER = "WAITER";
    public static final String ROLE_CASHIER = "CASHIER";

    /** FR-MNU-02/04: chỉ quản trị nhà hàng sửa món chung, quản lý chi nhánh sửa món riêng được phân công. */
    public void assertMenuDishWrite(Long restaurantId, Branch branch) {
        assertRestaurant(restaurantId);
        if (branch != null
                && (branch.getRestaurant() == null
                        || !Objects.equals(restaurantId, branch.getRestaurant().getId()))) {
            throw new AccessDeniedException("Chi nhánh không thuộc nhà hàng của món");
        }

        AccountUserDetails details = currentUserOrNull();
        if (details != null
                && details.getAuthorities().stream()
                        .anyMatch(
                                authority ->
                                        ("ROLE_" + ROLE_MANAGER)
                                                .equals(authority.getAuthority()))) {
            return;
        }
        if (details != null
                && details.getAccount().getAssignments() != null
                && branch != null
                && assignmentsMatching(details, new String[] {ROLE_BRANCH_MANAGER})
                        .anyMatch(branch.getId()::equals)) {
            return;
        }
        throw new AccessDeniedException(
                "Bạn chỉ được quản lý món riêng của chi nhánh được phân công");
    }

    /**
     * true nếu {@code accountId} chính là người đang đăng nhập.
     *
     * Dùng trong SpEL của {@code @PreAuthorize}, ví dụ:
     * {@code hasRole('MANAGER') or @tenantGuard.isSelf(#accountId)} — cần thiết vì màn
     * đăng nhập và thanh đổi chi nhánh của MỌI vai trò đều gọi
     * {@code GET /assignments/account/{id}} cho chính mình.
     */
    public boolean isSelf(Long accountId) {
        AccountUserDetails details = currentUserOrNull();
        return details != null
                && accountId != null
                && accountId.equals(details.getAccount().getId());
    }

    /**
     * BR-18 — chặn nếu người đăng nhập KHÔNG được phân công ở chi nhánh này.
     *
     * @param allowedRoleCodes chỉ tính những phân công có {@code role.code} nằm trong danh
     *                         sách. Bỏ trống = chấp nhận mọi vai trò.
     *
     * No-op khi: không chỉ định chi nhánh · request ẩn danh (luồng khách quét QR) ·
     * Quản trị nền tảng · người dùng có vai trò phạm vi NHÀ HÀNG (Quản trị NH được chạm
     * MỌI chi nhánh của nhà hàng mình — xét theo {@code role.scope}, KHÔNG theo
     * {@code assignment.branch}, vì seed gán MANAGER kèm branch_id=1).
     */
    public void assertBranchAssigned(Long branchId, String... allowedRoleCodes) {
        if (branchId == null) {
            return;
        }
        AccountUserDetails details = currentUserOrNull();
        if (details == null || isPlatformAdmin()) {
            return;
        }

        // Vẫn giữ chốt tenant cũ: chi nhánh của nhà hàng khác vẫn chặn như trước.
        assertBranch(branchId);

        if (hasRestaurantScopeRole(details)) {
            return;
        }

        boolean assigned =
                assignmentsMatching(details, allowedRoleCodes).anyMatch(branchId::equals);
        if (!assigned) {
            throw new AccessDeniedException("Bạn không được phân công ở chi nhánh này");
        }
    }

    public void assertBranchRole(Branch branch, String roleCode) {
        AccountUserDetails details = currentUserOrNull();
        if (details == null
                || !Objects.equals(details.getRestaurantId(), branch.getRestaurant().getId())
                || assignmentsMatching(details, roleCode).noneMatch(branch.getId()::equals)) {
            throw new AccessDeniedException("Bạn không được phân công quản lý chi nhánh này.");
        }
    }

    /**
     * Danh sách chi nhánh người đăng nhập được phân công — dùng để LỌC khi lời gọi không
     * chỉ định chi nhánh nào. Nếu chỉ chặn {@code /branch/{id}} mà bỏ đường này thì chỉ
     * cần đổi sang {@code ?restaurantId=} là đọc được cả nhà hàng.
     *
     * @return {@code null} = KHÔNG giới hạn (ẩn danh · Quản trị nền tảng · vai trò phạm vi
     *         nhà hàng). Danh sách rỗng = có đăng nhập nhưng chưa có phân công phù hợp ⇒
     *         chỗ gọi nên trả kết quả rỗng, không ném 403.
     */
    public List<Long> assignedBranchIdsOrNull(String... allowedRoleCodes) {
        AccountUserDetails details = currentUserOrNull();
        if (details == null || isPlatformAdmin() || hasRestaurantScopeRole(details)) {
            return null;
        }
        return assignmentsMatching(details, allowedRoleCodes).distinct().toList();
    }

    /** Có ít nhất một vai trò phạm vi NHÀ HÀNG (Quản trị NH) ⇒ mọi chi nhánh của nhà hàng. */
    private boolean hasRestaurantScopeRole(AccountUserDetails details) {
        Account account = details.getAccount();
        return account.getAssignments() != null
                && account.getAssignments().stream()
                        .map(Assignment::getRole)
                        .anyMatch(role -> role != null && role.getScope() == RoleScope.RESTAURANT);
    }

    /** Id chi nhánh của các phân công khớp danh sách vai trò (rỗng = mọi vai trò). */
    private Stream<Long> assignmentsMatching(
            AccountUserDetails details, String... allowedRoleCodes) {
        Set<String> allowed =
                allowedRoleCodes == null || allowedRoleCodes.length == 0
                        ? null
                        : Set.of(allowedRoleCodes);
        return details.getAccount().getAssignments().stream()
                .filter(a -> a.getBranch() != null && a.getRole() != null)
                .filter(a -> allowed == null || allowed.contains(a.getRole().getCode()))
                .map(a -> a.getBranch().getId());
    }

    private AccountUserDetails currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || !(auth.getPrincipal() instanceof AccountUserDetails details)) {
            return null;
        }
        return details;
    }
}
