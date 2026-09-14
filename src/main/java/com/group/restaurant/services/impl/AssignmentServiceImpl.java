package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.assignment.AssignmentAddRequest;
import com.group.restaurant.dtos.response.assignment.AssignmentResponse;
import com.group.restaurant.enums.RoleScope;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final AssignmentMapper assignmentMapper;

    @Override
    public AssignmentResponse createAssignment(AssignmentAddRequest request) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tài khoản với ID: " + request.getAccountId()));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy vai trò với ID: " + request.getRoleId()));

        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy chi nhánh với ID: " + request.getBranchId()));
        }

        if (role.getScope() == RoleScope.BRANCH && request.getBranchId() == null) {
            throw new InvalidParamException("Vai trò cấp chi nhánh phải chỉ định chi nhánh");
        }

        if ((role.getScope() == RoleScope.PLATFORM || role.getScope() == RoleScope.RESTAURANT)
                && request.getBranchId() != null) {
            throw new InvalidParamException("Vai trò cấp nền tảng/nhà hàng không được chỉ định chi nhánh");
        }

        Assignment assignment = assignmentMapper.toEntity(request);
        assignment.setAccount(account);
        assignment.setRole(role);
        assignment.setBranch(branch);

        Assignment savedAssignment = assignmentRepository.save(assignment);
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
        List<Assignment> assignments = assignmentRepository.findByAccountId(accountId);
        return assignments.stream()
                .map(this::buildAssignmentResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAssignmentsByRole(Long roleId) {
        List<Assignment> assignments = assignmentRepository.findByRoleId(roleId);
        return assignments.stream()
                .map(this::buildAssignmentResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAssignmentsByBranch(Long branchId) {
        List<Assignment> assignments = assignmentRepository.findByBranchId(branchId);
        return assignments.stream()
                .map(this::buildAssignmentResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);
        assignment.softDelete(null);
        assignmentRepository.save(assignment);
    }

    private Assignment findAssignmentById(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân công với ID: " + id));
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
                .branchName(assignment.getBranch() != null ? assignment.getBranch().getName() : null)
                .branchCode(assignment.getBranch() != null ? assignment.getBranch().getCode() : null)
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getModifiedAt())
                .build();
    }
}
