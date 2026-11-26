package com.group.restaurant.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.enums.AccountStatus;
import com.group.restaurant.enums.OperationalPermission;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.repositories.AccountRepository;
import com.group.restaurant.repositories.AssignmentRepository;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperationalPermissionService {
    private static final List<String> EDITABLE_ROLES =
            List.of("WAITER", "CASHIER", "BRANCH_MANAGER");

    private final RestaurantRepository restaurants;
    private final OrderNotificationService orderNotificationService;
    private final BranchRepository branches;
    private final AccountRepository accounts;
    private final AssignmentRepository assignments;
    private final AuditLogService auditLog;
    private final ObjectMapper mapper;

    public record PermissionItem(OperationalPermission code, String group, String label) {}

    public record RoleConfiguration(
            String role,
            long employeeCount,
            List<PermissionItem> available,
            Set<OperationalPermission> enabled) {}

    public record BranchPermissions(
            Long branchId, String branchName, List<PermissionItem> permissions) {}

    public record RoleUpdate(
            String role, Set<OperationalPermission> expected, Set<OperationalPermission> enabled) {}

    public List<RoleConfiguration> configuration() {
        var restaurant = findRestaurant(restaurantId(currentAccount()));
        var overrides = readOverrides(restaurant);
        return EDITABLE_ROLES.stream()
                .map(role -> describe(restaurant.getId(), role, overrides))
                .toList();
    }

    @Transactional
    public RoleConfiguration update(
            String role, Set<OperationalPermission> expected, Set<OperationalPermission> enabled) {
        return updateRoles(List.of(new RoleUpdate(role, expected, enabled))).getFirst();
    }

    @Transactional
    public List<RoleConfiguration> updateRoles(List<RoleUpdate> changes) {
        Set<String> seenRoles = new HashSet<>();
        for (var change : changes) {
            if (change == null
                    || change.role() == null
                    || !EDITABLE_ROLES.contains(change.role())
                    || !seenRoles.add(change.role())
                    || change.enabled() == null
                    || change.expected() == null
                    || change.enabled().stream()
                            .anyMatch(
                                    permission ->
                                            permission == null
                                                    || !permission.roles.contains(change.role()))) {
                throw new InvalidParamException("Vai trò hoặc quyền vận hành không hợp lệ.");
            }
            if (change.enabled().contains(OperationalPermission.EXPORT_REPORT)
                    && !change.enabled().contains(OperationalPermission.VIEW_REPORT)) {
                throw new InvalidParamException("Cần quyền xem báo cáo để xuất báo cáo.");
            }
        }
        Long restaurantId = restaurantId(currentAccount());
        var restaurant =
                restaurants
                        .findByIdForUpdate(restaurantId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Không tìm thấy nhà hàng."));
        var overrides = readOverrides(restaurant);
        for (var change : changes) {
            if (!permissionsFor(change.role(), overrides).equals(change.expected())) {
                throw new ConflictException("Quyền đã thay đổi. Vui lòng tải lại trước khi lưu.");
            }
        }
        var changedRoles =
                changes.stream()
                        .filter(change -> !change.expected().equals(change.enabled()))
                        .toList();
        if (!changedRoles.isEmpty()) {
            changedRoles.forEach(change -> overrides.put(change.role(), change.enabled()));
            restaurant.setOperationalPermissions(writeOverrides(overrides));
            orderNotificationService.notifyManagementUpdated(restaurantId);
        }
        for (var change : changedRoles) {
            auditLog.recordInTransaction(
                    AuditEvent.builder()
                            .entityType("OperationalPermission")
                            .entityId(restaurantId)
                            .restaurantId(restaurantId)
                            .actionType(AuditAction.PERMISSION_CHANGE)
                            .valueBefore(auditValue(change.role(), change.expected()))
                            .valueAfter(auditValue(change.role(), change.enabled()))
                            .reason("Cập nhật quyền vận hành theo vai trò")
                            .build());
        }
        return changes.stream()
                .map(change -> describe(restaurantId, change.role(), overrides))
                .toList();
    }

    public List<BranchPermissions> employeePermissions(Long employeeId) {
        Long restaurantId = restaurantId(currentAccount());
        var employee =
                accounts.findByIdWithAssignments(employeeId)
                        .filter(
                                account ->
                                        account.getRestaurant() != null
                                                && restaurantId.equals(
                                                        account.getRestaurant().getId()))
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Không tìm thấy nhân viên."));
        var overrides = readOverrides(findRestaurant(restaurantId));
        if (isManager(employee)) {
            return List.of(
                    new BranchPermissions(
                            null, "Toàn nhà hàng", items(effective(employee, null, overrides))));
        }
        return employee.getAssignments().stream()
                .map(assignment -> assignment.getBranch())
                .filter(Objects::nonNull)
                .collect(
                        Collectors.toMap(
                                Branch::getId,
                                branch -> branch,
                                (left, right) -> left,
                                TreeMap::new))
                .values()
                .stream()
                .map(
                        branch ->
                                new BranchPermissions(
                                        branch.getId(),
                                        branch.getName(),
                                        items(effective(employee, branch.getId(), overrides))))
                .toList();
    }

    public Set<OperationalPermission> currentPermissions(Long branchId) {
        Account account = currentAccount();
        Long restaurantId = restaurantId(account);
        Branch branch =
                branches.findById(branchId)
                        .filter(candidate -> restaurantId.equals(candidate.getRestaurant().getId()))
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Chi nhánh ngoài phạm vi nhà hàng."));
        return effective(account, branch.getId(), readOverrides(findRestaurant(restaurantId)));
    }

    public void require(Long branchId, OperationalPermission permission) {
        if (!currentPermissions(branchId).contains(permission)) {
            throw new AccessDeniedException(
                    "Bạn chưa có quyền " + permission.label + " tại chi nhánh này.");
        }
    }

    public void requireManager(Long restaurantId) {
        Account account = currentAccount();
        if (!restaurantId(account).equals(restaurantId) || !isManager(account)) {
            throw new AccessDeniedException("Chỉ Quản trị nhà hàng được xem báo cáo toàn chuỗi.");
        }
    }

    private Set<OperationalPermission> effective(
            Account account, Long branchId, Map<String, Set<OperationalPermission>> overrides) {
        var enabled = EnumSet.noneOf(OperationalPermission.class);
        if (account.getStatus() != AccountStatus.ACTIVE) return enabled;
        if (isManager(account)) return EnumSet.allOf(OperationalPermission.class);
        for (var assignment : account.getAssignments()) {
            if (assignment.getRole() != null
                    && assignment.getBranch() != null
                    && branchId.equals(assignment.getBranch().getId())) {
                enabled.addAll(permissionsFor(assignment.getRole().getCode(), overrides));
            }
        }
        return enabled;
    }

    private boolean isManager(Account account) {
        return account.getAssignments().stream()
                .anyMatch(
                        assignment ->
                                assignment.getRole() != null
                                        && "MANAGER".equals(assignment.getRole().getCode()));
    }

    private RoleConfiguration describe(
            Long restaurantId, String role, Map<String, Set<OperationalPermission>> overrides) {
        var available =
                Arrays.stream(OperationalPermission.values())
                        .filter(permission -> permission.roles.contains(role))
                        .map(this::item)
                        .toList();
        return new RoleConfiguration(
                role,
                assignments.countAccountsForPermissions(restaurantId, role, AccountStatus.ACTIVE),
                available,
                permissionsFor(role, overrides));
    }

    private Set<OperationalPermission> permissionsFor(
            String role, Map<String, Set<OperationalPermission>> overrides) {
        var configured = overrides.get(role);
        return Arrays.stream(OperationalPermission.values())
                .filter(
                        permission ->
                                permission.roles.contains(role)
                                        && (configured == null
                                                ? permission.defaults.contains(role)
                                                : configured.contains(permission)))
                .collect(
                        Collectors.toCollection(() -> EnumSet.noneOf(OperationalPermission.class)));
    }

    private List<PermissionItem> items(Set<OperationalPermission> permissions) {
        return permissions.stream().map(this::item).toList();
    }

    private PermissionItem item(OperationalPermission permission) {
        return new PermissionItem(permission, permission.group, permission.label);
    }

    private String auditValue(String role, Set<OperationalPermission> permissions) {
        return role
                + " · "
                + permissions.stream()
                        .sorted()
                        .map(permission -> permission.label)
                        .collect(Collectors.joining(", "));
    }

    private Account currentAccount() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountUserDetails details)
            return details.getAccount();
        throw new AccessDeniedException("Cần đăng nhập bằng tài khoản nhân viên.");
    }

    private Long restaurantId(Account account) {
        if (account.getRestaurant() != null) return account.getRestaurant().getId();
        throw new AccessDeniedException("Tài khoản chưa thuộc nhà hàng.");
    }

    private Restaurant findRestaurant(Long id) {
        return restaurants
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà hàng."));
    }

    private Map<String, Set<OperationalPermission>> readOverrides(Restaurant restaurant) {
        if (restaurant.getOperationalPermissions() == null) return new TreeMap<>();
        try {
            return mapper.readValue(
                    restaurant.getOperationalPermissions(),
                    new TypeReference<TreeMap<String, Set<OperationalPermission>>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không đọc được cấu hình quyền vận hành.", exception);
        }
    }

    private String writeOverrides(Map<String, Set<OperationalPermission>> overrides) {
        try {
            return mapper.writeValueAsString(overrides);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không lưu được cấu hình quyền vận hành.", exception);
        }
    }
}
