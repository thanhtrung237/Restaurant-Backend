package com.group.restaurant.services.impl;

import com.group.restaurant.services.OrderNotificationService;
import com.group.restaurant.dtos.request.assignment.AssignmentAddRequest;
import com.group.restaurant.dtos.response.assignment.AssignmentResponse;
import com.group.restaurant.enums.AccountStatus;
import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.AssignmentMapper;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.Assignment;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Role;
import com.group.restaurant.repositories.AccountRepository;
import com.group.restaurant.repositories.AssignmentRepository;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.RoleRepository;
import com.group.restaurant.services.AssignmentService;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.utils.TenantGuard;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final OrderNotificationService orderNotificationService;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final AssignmentMapper assignmentMapper;
    private final AuditLogService auditLogService;
    private final TenantGuard tenantGuard;

    @Override
    public AssignmentResponse createAssignment(AssignmentAddRequest request) {
        Account account =
                accountRepository
                        .findById(request.getAccountId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy tài khoản với ID: "
                                                        + request.getAccountId()));

        Role role =
                roleRepository
                        .findById(request.getRoleId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy vai trò với ID: "
                                                        + request.getRoleId()));

        Branch branch = null;
        if (request.getBranchId() != null) {
            branch =
                    branchRepository
                            .findById(request.getBranchId())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Không tìm thấy chi nhánh với ID: "
                                                            + request.getBranchId()));
        }

        // Chặn leo quyền xuyên tenant: tài khoản, vai trò và chi nhánh phải cùng
        // thuộc nhà hàng của người đang đăng nhập. Không có 3 dòng này thì một Quản trị
        // nhà hàng A có thể gán vai trò của mình cho tài khoản nhà hàng B (hoặc ngược lại).
        tenantGuard.assertRestaurant(
                account.getRestaurant() != null ? account.getRestaurant().getId() : null);
        tenantGuard.assertRestaurant(
                role.getRestaurant() != null ? role.getRestaurant().getId() : null);
        tenantGuard.assertBranch(branch);

        if (role.getScope() == RoleScope.BRANCH && request.getBranchId() == null) {
            throw new InvalidParamException("Vai trò cấp chi nhánh phải chỉ định chi nhánh");
        }

        if ((role.getScope() == RoleScope.PLATFORM || role.getScope() == RoleScope.RESTAURANT)
                && request.getBranchId() != null) {
            throw new InvalidParamException(
                    "Vai trò cấp nền tảng/nhà hàng không được chỉ định chi nhánh");
        }

        Assignment assignment = assignmentMapper.toEntity(request);
        assignment.setAccount(account);
        assignment.setRole(role);
        assignment.setBranch(branch);

        Assignment savedAssignment = assignmentRepository.save(assignment);
        orderNotificationService.notifyManagementUpdated(assignment.getAccount().getRestaurant() == null ? null : assignment.getAccount().getRestaurant().getId());

        auditLogService.record(
                AuditEvent.builder()
                        .entityType("Assignment")
                        .entityId(savedAssignment.getId())
                        .actionType(AuditAction.PERMISSION_CHANGE)
                        .branchId(branch != null ? branch.getId() : null)
                        .restaurantId(
                                role.getRestaurant() != null ? role.getRestaurant().getId() : null)
                        .valueAfter(
                                "account="
                                        + account.getUsername()
                                        + "; role="
                                        + role.getCode()
                                        + "; scope="
                                        + role.getScope()
                                        + "; branch="
                                        + (branch != null ? branch.getCode() : "-"))
                        .reason("Cấp vai trò cho tài khoản")
                        .build());

        return buildAssignmentResponse(savedAssignment);
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse getAssignmentById(Long id) {
        Assignment assignment = findAssignmentById(id);
        return buildAssignmentResponse(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAssignmentsByAccount(Long accountId) {
        return assignmentRepository.findByAccountId(accountId).stream()
                .map(this::buildAssignmentResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAssignmentsByRole(Long roleId) {
        List<Assignment> assignments = assignmentRepository.findByRoleId(roleId);
        return assignments.stream().map(this::buildAssignmentResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAssignmentsByBranch(Long branchId) {
        // BR-18 — Bảng 4.3 "Quản lý nhân viên": ngoài Quản trị nhà hàng, chỉ QL chi nhánh
        // của ĐÚNG chi nhánh này được xem danh sách nhân sự của nó.
        tenantGuard.assertBranchAssigned(branchId, TenantGuard.ROLE_BRANCH_MANAGER);
        List<Assignment> assignments = assignmentRepository.findByBranchId(branchId);
        return assignments.stream().map(this::buildAssignmentResponse).collect(Collectors.toList());
    }

    @Override
    public void deleteAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);
        assertNotLastManagerAssignment(assignment);
        assignment.softDelete(null);
        assignmentRepository.save(assignment);
        orderNotificationService.notifyManagementUpdated(assignment.getAccount().getRestaurant() == null ? null : assignment.getAccount().getRestaurant().getId());

        auditLogService.record(
                AuditEvent.builder()
                        .entityType("Assignment")
                        .entityId(id)
                        .actionType(AuditAction.PERMISSION_CHANGE)
                        .branchId(
                                assignment.getBranch() != null
                                        ? assignment.getBranch().getId()
                                        : null)
                        .valueBefore(
                                "account="
                                        + assignment.getAccount().getUsername()
                                        + "; role="
                                        + assignment.getRole().getCode())
                        .reason("Thu hồi vai trò của tài khoản")
                        .build());
    }

    /**
     * Không cho gỡ vai trò Quản trị nhà hàng của người CUỐI CÙNG còn hoạt động.
     *
     * Cùng lý do với `AccountServiceImpl.assertNotLastActiveManager`: gỡ nốt phân công này
     * là không còn ai gọi được các endpoint `hasRole('MANAGER')` để cấp lại.
     */
    private void assertNotLastManagerAssignment(Assignment assignment) {
        Role role = assignment.getRole();
        if (role == null || !TenantGuard.ROLE_MANAGER.equals(role.getCode())) {
            return;
        }
        Account owner = assignment.getAccount();
        Long restaurantId =
                owner != null && owner.getRestaurant() != null
                        ? owner.getRestaurant().getId()
                        : null;
        long others =
                assignmentRepository.countActiveAccountsWithRoleExcludingAssignment(
                        TenantGuard.ROLE_MANAGER,
                        restaurantId,
                        AccountStatus.ACTIVE,
                        assignment.getId());
        if (others == 0) {
            throw new ConflictException(
                    "Không thể gỡ vai trò Quản trị nhà hàng của người cuối cùng còn hoạt động. "
                            + "Hãy cấp vai trò này cho một tài khoản khác trước.");
        }
    }

    /** Chốt tenant tại finder: phủ getAssignmentById / deleteAssignment. */
    private Assignment findAssignmentById(Long id) {
        Assignment assignment =
                assignmentRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy phân công với ID: " + id));
        Account owner = assignment.getAccount();
        tenantGuard.assertRestaurant(
                owner != null && owner.getRestaurant() != null
                        ? owner.getRestaurant().getId()
                        : null);
        return assignment;
    }

    private AssignmentResponse buildAssignmentResponse(Assignment assignment) {
        return AssignmentResponse.builder()
                .id(assignment.getId())
                .accountId(assignment.getAccount().getId())
                .accountName(assignment.getAccount().getFullName())
                .employeeCode(assignment.getAccount().getEmployeeCode())
                .roleId(assignment.getRole().getId())
                .roleName(assignment.getRole().getName())
                .roleCode(assignment.getRole().getCode())
                .roleScope(assignment.getRole().getScope())
                .branchId(assignment.getBranch() != null ? assignment.getBranch().getId() : null)
                .branchName(
                        assignment.getBranch() != null ? assignment.getBranch().getName() : null)
                .branchCode(
                        assignment.getBranch() != null ? assignment.getBranch().getCode() : null)
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getModifiedAt())
                .build();
    }
}
