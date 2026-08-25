package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.account.AccountAddRequest;
import com.group.restaurant.dtos.request.account.AccountUpdateRequest;
import com.group.restaurant.dtos.response.account.AccountResponse;
import com.group.restaurant.dtos.response.assignment.AssignmentResponse;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.enums.AccountStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.AccountMapper;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.Assignment;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.repositories.AccountRepository;
import com.group.restaurant.repositories.AssignmentRepository;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.services.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final RestaurantRepository restaurantRepository;
    private final AssignmentRepository assignmentRepository;
    private final AccountMapper accountMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public AccountResponse createAccount(AccountAddRequest request) {
        if (accountRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Tên đăng nhập '" + request.getUsername() + "' đã tồn tại");
        }

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhà hàng với ID: " + request.getRestaurantId()));

        Account account = accountMapper.toEntity(request);
        account.setRestaurant(restaurant);
        account.setPasswordHash(hashPassword(request.getPassword()));
        account.setStatus(AccountStatus.ACTIVE);

        Account savedAccount = accountRepository.save(account);
        return buildAccountResponse(savedAccount);
    }

    @Override
    public AccountResponse updateAccount(Long id, AccountUpdateRequest request) {
        Account account = findAccountById(id);

        accountMapper.updateEntity(account, request);
        Account updatedAccount = accountRepository.save(account);

        return buildAccountResponse(updatedAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long id) {
        Account account = accountRepository.findByIdWithAssignments(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với ID: " + id));
        return buildAccountResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountByUsername(String username) {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy tài khoản với tên đăng nhập: " + username));
        return buildAccountResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> getAllAccounts(
            Long restaurantId,
            AccountStatus status,
            Pageable pageable) {
        Page<Account> accountPage;

        if (restaurantId != null && status != null) {
            List<Account> filteredAccounts = accountRepository.findByRestaurantIdAndStatus(restaurantId, status);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filteredAccounts.size());
            List<Account> pageContent = filteredAccounts.subList(start, end);

            List<AccountResponse> responses = pageContent.stream()
                    .map(this::buildAccountResponse)
                    .collect(Collectors.toList());

            return PageResponse.<AccountResponse>builder()
                    .content(responses)
                    .pageNumber(pageable.getPageNumber())
                    .pageSize(pageable.getPageSize())
                    .totalElements((long) filteredAccounts.size())
                    .totalPages((int) Math.ceil((double) filteredAccounts.size() / pageable.getPageSize()))
                    .first(pageable.getPageNumber() == 0)
                    .last(end >= filteredAccounts.size())
                    .build();
        } else if (restaurantId != null) {
            List<Account> filteredAccounts = accountRepository.findByRestaurantId(restaurantId);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filteredAccounts.size());
            List<Account> pageContent = filteredAccounts.subList(start, end);

            List<AccountResponse> responses = pageContent.stream()
                    .map(this::buildAccountResponse)
                    .collect(Collectors.toList());

            return PageResponse.<AccountResponse>builder()
                    .content(responses)
                    .pageNumber(pageable.getPageNumber())
                    .pageSize(pageable.getPageSize())
                    .totalElements((long) filteredAccounts.size())
                    .totalPages((int) Math.ceil((double) filteredAccounts.size() / pageable.getPageSize()))
                    .first(pageable.getPageNumber() == 0)
                    .last(end >= filteredAccounts.size())
                    .build();
        } else {
            accountPage = accountRepository.findAll(pageable);

            List<AccountResponse> responses = accountPage.getContent().stream()
                    .map(this::buildAccountResponse)
                    .collect(Collectors.toList());

            return PageResponse.<AccountResponse>builder()
                    .content(responses)
                    .pageNumber(accountPage.getNumber())
                    .pageSize(accountPage.getSize())
                    .totalElements(accountPage.getTotalElements())
                    .totalPages(accountPage.getTotalPages())
                    .first(accountPage.isFirst())
                    .last(accountPage.isLast())
                    .build();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByRestaurant(Long restaurantId) {
        List<Account> accounts = accountRepository.findByRestaurantId(restaurantId);
        return accounts.stream()
                .map(this::buildAccountResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void changeStatus(Long id, AccountStatus status) {
        Account account = findAccountById(id);
        account.setStatus(status);
        accountRepository.save(account);
    }

    @Override
    public void changePassword(Long id, String oldPassword, String newPassword) {
        Account account = findAccountById(id);

        if (!verifyPassword(oldPassword, account.getPasswordHash())) {
            throw new InvalidParamException("Mật khẩu cũ không đúng");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new InvalidParamException("Mật khẩu mới phải có ít nhất 8 ký tự");
        }

        account.setPasswordHash(hashPassword(newPassword));
        accountRepository.save(account);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        Account account = findAccountById(id);

        if (newPassword == null || newPassword.length() < 8) {
            throw new InvalidParamException("Mật khẩu mới phải có ít nhất 8 ký tự");
        }

        account.setPasswordHash(hashPassword(newPassword));
        accountRepository.save(account);
    }

    @Override
    public void deleteAccount(Long id) {
        Account account = findAccountById(id);

        long assignmentCount = assignmentRepository.findByAccountId(id).size();
        if (assignmentCount > 0) {
            throw new ConflictException("Không thể xóa tài khoản đang có " + assignmentCount + " phân công");
        }

        account.softDelete(null);
        accountRepository.save(account);
    }

    private Account findAccountById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với ID: " + id));
    }

    private String hashPassword(String plainPassword) {
        return passwordEncoder.encode(plainPassword);
    }

    private boolean verifyPassword(String plainPassword, String hashedPassword) {
        return passwordEncoder.matches(plainPassword, hashedPassword);
    }

    private AccountResponse buildAccountResponse(Account account) {
        AccountResponse response = accountMapper.toResponse(account);

        List<AssignmentResponse> assignmentResponses = new ArrayList<>();
        if (account.getAssignments() != null && !account.getAssignments().isEmpty()) {
            assignmentResponses = account.getAssignments().stream()
                    .map(this::buildAssignmentResponse)
                    .collect(Collectors.toList());
        }
        response.setAssignments(assignmentResponses);

        return response;
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