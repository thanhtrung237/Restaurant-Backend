package com.group.restaurant.services;

import com.group.restaurant.dtos.request.account.AccountAddRequest;
import com.group.restaurant.enums.AccountStatus;
import com.group.restaurant.enums.BranchStatus;
import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.Assignment;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Role;
import com.group.restaurant.repositories.AccountRepository;
import com.group.restaurant.repositories.AssignmentRepository;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.RoleRepository;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.utils.TenantGuard;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchStaffService {
    private static final Set<String> STAFF_ROLES = Set.of(TenantGuard.ROLE_WAITER, TenantGuard.ROLE_CASHIER);

    private final BranchRepository branches;
    private final AccountRepository accounts;
    private final AssignmentRepository assignments;
    private final RoleRepository roles;
    private final AccountService accountService;
    private final AuditLogService audit;
    private final TenantGuard tenantGuard;

    public record StaffMember(Long id, String fullName, String username, String employeeCode, AccountStatus status,
                              List<String> roles, boolean editable) {}

    public record CreateStaff(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Size(max = 50) String username,
            @Email @Size(max = 100) String email,
            @NotBlank @Size(min = 8) String password,
            @NotEmpty @Size(max = 2) Set<@NotBlank String> roles) {}

    public record UpdateRoles(@NotEmpty @Size(max = 2) Set<@NotBlank String> expected,
                              @NotEmpty @Size(max = 2) Set<@NotBlank String> roles) {}

    public List<StaffMember> list(Long branchId) {
        Branch branch = managedBranch(branchId);
        return assignments.findForBranchStaff(branchId).stream()
                .filter(assignment -> assignment.getAccount().getRestaurant() != null
                        && branch.getRestaurant().getId().equals(assignment.getAccount().getRestaurant().getId()))
                .collect(Collectors.groupingBy(assignment -> assignment.getAccount().getId()))
                .values().stream()
                .map(entries -> member(entries.getFirst().getAccount(), branch, entries))
                .sorted(Comparator.comparing(StaffMember::fullName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(StaffMember::id))
                .toList();
    }

    @Transactional
    public StaffMember create(Long branchId, CreateStaff request) {
        Branch branch = managedBranch(branchId);
        assertActive(branch);
        List<Role> selected = resolveRoles(branch, request.roles());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidParamException("Mật khẩu không được vượt quá 72 byte.");
        }
        var accountRequest = new AccountAddRequest();
        accountRequest.setRestaurantId(branch.getRestaurant().getId());
        accountRequest.setFullName(request.fullName().strip());
        accountRequest.setUsername(request.username().strip());
        accountRequest.setEmail(request.email() == null || request.email().isBlank() ? null : request.email().strip());
        accountRequest.setPassword(request.password());
        var created = accountService.createAccount(accountRequest);
        Account account = accounts.getReferenceById(created.getId());
        var entries = selected.stream().map(role -> addAssignment(account, branch, role)).toList();
        recordChange(account, branch, List.of(), roleCodes(entries), "Tạo tài khoản và phân công nhân viên chi nhánh");
        return member(account, branch, entries);
    }

    @Transactional
    public StaffMember update(Long branchId, Long accountId, UpdateRoles request) {
        Branch branch = managedBranch(branchId);
        assertActive(branch);
        List<Role> selected = resolveRoles(branch, request.roles());
        Account account = accounts.findForAssignmentUpdate(accountId, branch.getRestaurant().getId())
                .orElseThrow(BranchStaffService::staffDenied);
        List<Assignment> all = new ArrayList<>(assignments.findByAccountId(accountId));
        List<Assignment> local = branchAssignments(all, branchId);
        if (!canEdit(account, local, all)) throw staffDenied();
        List<String> before = roleCodes(local);
        if (!request.expected().equals(Set.copyOf(before))) {
            throw new ConflictException("Vai trò đã thay đổi. Vui lòng tải lại danh sách trước khi lưu.");
        }
        if (request.roles().equals(request.expected())) return member(account, branch, all);

        local.stream().filter(assignment -> !request.roles().contains(assignment.getRole().getCode()))
                .forEach(assignment -> assignment.softDelete(null));
        selected.stream().filter(role -> !before.contains(role.getCode()))
                .map(role -> addAssignment(account, branch, role)).forEach(all::add);
        List<String> after = request.roles().stream().sorted().toList();
        recordChange(account, branch, before, after, "Cập nhật phân công nhân viên chi nhánh");
        return member(account, branch, all);
    }

    private Branch managedBranch(Long branchId) {
        Branch branch = branches.findById(branchId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh."));
        tenantGuard.assertBranchRole(branch, TenantGuard.ROLE_BRANCH_MANAGER);
        return branch;
    }

    private void assertActive(Branch branch) {
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new ConflictException("Chi nhánh đang ngừng hoạt động.");
        }
    }

    private List<Role> resolveRoles(Branch branch, Set<String> requested) {
        if (requested.isEmpty() || !STAFF_ROLES.containsAll(requested)) {
            throw new InvalidParamException("Chỉ được phân công vai trò Phục vụ hoặc Thu ngân.");
        }
        return requested.stream().sorted().map(code -> roles.findByRestaurantIdAndCode(branch.getRestaurant().getId(), code)
                .filter(role -> role.getScope() == RoleScope.BRANCH)
                .orElseThrow(() -> new ConflictException("Nhà hàng chưa cấu hình vai trò " + code + " cho chi nhánh.")))
                .toList();
    }

    private Assignment addAssignment(Account account, Branch branch, Role role) {
        return assignments.save(Assignment.builder().account(account).branch(branch).role(role).build());
    }

    private StaffMember member(Account account, Branch branch, List<Assignment> entries) {
        List<Assignment> local = branchAssignments(entries, branch.getId());
        return new StaffMember(account.getId(), account.getFullName(), account.getUsername(), account.getEmployeeCode(),
                account.getStatus(), roleCodes(local), branch.getStatus() == BranchStatus.ACTIVE && canEdit(account, local, entries));
    }

    private List<Assignment> branchAssignments(List<Assignment> entries, Long branchId) {
        return entries.stream().filter(entry -> !Boolean.TRUE.equals(entry.getIsDeleted()) && entry.getBranch() != null
                && Objects.equals(entry.getBranch().getId(), branchId)).toList();
    }

    private boolean canEdit(Account account, List<Assignment> local, List<Assignment> all) {
        return account.getStatus() == AccountStatus.ACTIVE && !tenantGuard.isSelf(account.getId()) && !local.isEmpty()
                && local.stream().allMatch(entry -> entry.getRole().getScope() == RoleScope.BRANCH
                        && STAFF_ROLES.contains(entry.getRole().getCode()))
                && all.stream().noneMatch(entry -> entry.getRole().getScope() != RoleScope.BRANCH);
    }

    private List<String> roleCodes(List<Assignment> entries) {
        return entries.stream().map(entry -> entry.getRole().getCode()).distinct().sorted().toList();
    }

    private static AccessDeniedException staffDenied() {
        return new AccessDeniedException("Bạn chỉ được chỉnh phân công của Phục vụ và Thu ngân tại chi nhánh mình.");
    }

    private void recordChange(Account account, Branch branch, List<String> before, List<String> after, String reason) {
        audit.recordInTransaction(AuditEvent.builder().entityType("BranchStaffAssignment").entityId(account.getId())
                .actionType(AuditAction.PERMISSION_CHANGE).branchId(branch.getId())
                .restaurantId(branch.getRestaurant().getId())
                .valueBefore("account=" + account.getUsername() + "; roles=" + before)
                .valueAfter("account=" + account.getUsername() + "; roles=" + after)
                .reason(reason).build());
    }
}
