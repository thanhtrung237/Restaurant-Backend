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
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.services.AccountService;
import com.group.restaurant.utils.TenantGuard;
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
    private final AuditLogService auditLogService;
    private final TenantGuard tenantGuard;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** Khoá sinh mã nhân viên — xem ghi chú ở generateEmployeeCode. */
    private static final Object EMPLOYEE_CODE_LOCK = new Object();

    @Override
    public AccountResponse createAccount(AccountAddRequest request) {
        tenantGuard.assertRestaurant(request.getRestaurantId());
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhà hàng với ID: " + request.getRestaurantId()));

        // Trùng tên đăng nhập xét TRONG PHẠM VI nhà hàng, đúng với ràng buộc DB
        // `uk_account_restaurant_username = (restaurant_id, username)`.
        // `existsByUsername` cũ xét toàn hệ thống nên chặn oan: nhà hàng B không tạo được
        // tài khoản "admin" chỉ vì nhà hàng A đã có.
        if (accountRepository.existsByRestaurantIdAndUsername(restaurant.getId(), request.getUsername())) {
            throw new ConflictException("Tên đăng nhập '" + request.getUsername()
                    + "' đã tồn tại trong nhà hàng này");
        }

        Account account = accountMapper.toEntity(request);
        account.setRestaurant(restaurant);
        account.setPasswordHash(hashPassword(request.getPassword()));
        account.setStatus(AccountStatus.ACTIVE);

        // QĐ-03: mã nhân viên do hệ thống sinh, client không gửi.
        Account savedAccount;
        synchronized (EMPLOYEE_CODE_LOCK) {
            account.setEmployeeCode(generateEmployeeCode(restaurant.getId()));
            savedAccount = accountRepository.save(account);
        }
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
        // Dùng repository trực tiếp (cần fetch assignments) nên KHÔNG đi qua
        // findAccountById() — phải tự gọi guard, nếu không thì đây là lỗ đọc tài khoản
        // của nhà hàng khác.
        Account account = accountRepository.findByIdWithAssignments(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với ID: " + id));
        tenantGuard.assertRestaurant(account.getRestaurant() != null ? account.getRestaurant().getId() : null);
        return buildAccountResponse(account);
    }

    /**
     * Tra tài khoản theo tên đăng nhập.
     *
     * `username` chỉ unique theo từng nhà hàng, nên tra toàn hệ thống là sai từ gốc:
     * 2 nhà hàng cùng có "admin" thì `findByUsername` trả 2 hàng cho một Optional → lỗi.
     * Vì vậy luôn giới hạn trong nhà hàng của người đang đăng nhập.
     */
    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountByUsername(String username) {
        Long restaurantId = tenantGuard.currentRestaurantIdOrNull();
        if (restaurantId == null) {
            throw new ResourceNotFoundException("Không tìm thấy tài khoản với tên đăng nhập: " + username);
        }
        Account account = accountRepository.findByRestaurantIdAndUsername(restaurantId, username)
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
        tenantGuard.assertRestaurant(restaurantId);

        // Không truyền restaurantId thì nhánh cuối gọi findAll() — trả tài khoản của
        // MỌI nhà hàng. Mặc định giới hạn vào nhà hàng của người đang đăng nhập;
        // chỉ Quản trị nền tảng mới được xem xuyên nhà hàng.
        if (restaurantId == null && !tenantGuard.isPlatformAdmin()) {
            restaurantId = tenantGuard.currentRestaurantIdOrNull();
        }

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
        tenantGuard.assertRestaurant(restaurantId);
        List<Account> accounts = accountRepository.findByRestaurantId(restaurantId);
        return accounts.stream()
                .map(this::buildAccountResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void changeStatus(Long id, AccountStatus status) {
        Account account = findAccountById(id);

        // Chặn tự khoá mình ra khỏi hệ thống. Hai trường hợp đều không có đường quay lại:
        // người vừa bị khoá không đăng nhập được để tự mở, mà cũng không còn ai khác mở hộ.
        if (status != AccountStatus.ACTIVE) {
            assertNotSelf(account);
            assertNotLastActiveManager(account);
        }

        AccountStatus previous = account.getStatus();
        account.setStatus(status);
        accountRepository.save(account);

        auditLogService.record(AuditEvent.builder()
                .entityType("Account")
                .entityId(id)
                .actionType(AuditAction.PERMISSION_CHANGE)
                .restaurantId(account.getRestaurant() != null ? account.getRestaurant().getId() : null)
                .valueBefore("status=" + previous)
                .valueAfter("status=" + status)
                .reason("Đổi trạng thái tài khoản " + account.getUsername())
                .build());
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

        // KHÔNG ghi mật khẩu (kể cả hash) vào valueAfter — nhật ký là dữ liệu đọc được
        // bởi mọi quản trị viên. Chỉ ghi sự kiện "đã reset".
        auditLogService.record(AuditEvent.builder()
                .entityType("Account")
                .entityId(id)
                .actionType(AuditAction.PERMISSION_CHANGE)
                .restaurantId(account.getRestaurant() != null ? account.getRestaurant().getId() : null)
                .valueAfter("passwordReset=true")
                .reason("Quản trị viên đặt lại mật khẩu cho " + account.getUsername())
                .build());
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

    /**
     * Sinh mã nhân viên kế tiếp của nhà hàng: `NV` + số thứ tự, tối thiểu 3 chữ số.
     *
     * Lấy số lớn nhất đang dùng trong CHÍNH nhà hàng đó rồi +1, và vẫn kiểm trùng trước khi
     * trả về — dữ liệu cũ có thể có mã nhập tay không theo quy ước.
     *
     * ⚠️ Giới hạn đã biết: cột `employee_code` KHÔNG có ràng buộc duy nhất ở DB, nên hai lời
     * gọi tạo tài khoản thật sự đồng thời (hai tiến trình) vẫn có thể ra cùng một mã. Khối
     * `synchronized` chỉ chặn được trong một tiến trình. Cách sửa triệt để là thêm unique
     * index `(restaurant_id, employee_code)` — đổi schema nên chưa làm.
     */
    private String generateEmployeeCode(Long restaurantId) {
        int maxNumber = accountRepository.findByRestaurantId(restaurantId).stream()
                .map(Account::getEmployeeCode)
                .filter(code -> code != null && code.matches("^NV\\d+$"))
                .map(code -> Integer.parseInt(code.substring(2)))
                .max(Integer::compareTo)
                .orElse(0);

        for (int next = maxNumber + 1; next < maxNumber + 1000; next++) {
            String candidate = String.format("NV%03d", next);
            if (accountRepository.findByRestaurantIdAndEmployeeCode(restaurantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ConflictException("Không sinh được mã nhân viên mới, vui lòng thử lại");
    }

    /** Không cho tự vô hiệu hoá chính mình. */
    private void assertNotSelf(Account account) {
        if (tenantGuard.isSelf(account.getId())) {
            throw new ConflictException(
                    "Không thể vô hiệu hoá tài khoản của chính bạn. Nhờ một Quản trị nhà hàng khác thực hiện.");
        }
    }

    /**
     * Không cho vô hiệu hoá Quản trị nhà hàng CUỐI CÙNG còn hoạt động.
     *
     * Mất người cuối cùng giữ vai trò này là mất luôn đường vào các màn quản trị: tạo tài
     * khoản, phân công vai trò, mở lại tài khoản bị khoá đều là `hasRole('MANAGER')`.
     */
    private void assertNotLastActiveManager(Account account) {
        boolean isManager = assignmentRepository.findByAccountId(account.getId()).stream()
                .anyMatch(a -> a.getRole() != null
                        && TenantGuard.ROLE_MANAGER.equals(a.getRole().getCode()));
        if (!isManager) {
            return;
        }
        Long restaurantId = account.getRestaurant() != null ? account.getRestaurant().getId() : null;
        long others = assignmentRepository.countActiveAccountsWithRoleExcludingAccount(
                TenantGuard.ROLE_MANAGER, restaurantId, AccountStatus.ACTIVE, account.getId());
        if (others == 0) {
            throw new ConflictException(
                    "Không thể vô hiệu hoá Quản trị nhà hàng cuối cùng còn hoạt động. "
                    + "Hãy cấp vai trò này cho một tài khoản khác trước.");
        }
    }

    /**
      * Chốt tenant đặt tại finder dùng chung: phủ getAccountById / updateAccount /
      * changeStatus / changePassword / resetPassword / deleteAccount.
      * Nếu không có chốt này, một Quản trị nhà hàng A gọi
      * `PUT /accounts/{id}/reset-password` với id của nhà hàng B là chiếm được tài khoản đó.
      */
    private Account findAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với ID: " + id));
        tenantGuard.assertRestaurant(account.getRestaurant() != null ? account.getRestaurant().getId() : null);
        return account;
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