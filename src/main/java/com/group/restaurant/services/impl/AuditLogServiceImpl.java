package com.group.restaurant.services.impl;

import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.dtos.response.auditLog.AuditLogResponse;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.AuditLogMapper;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.AuditLog;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.repositories.AccountRepository;
import com.group.restaurant.repositories.AssignmentRepository;
import com.group.restaurant.repositories.AuditLogRepository;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.utils.TenantGuard;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AccountRepository accountRepository;
    private final AssignmentRepository assignmentRepository;
    private final BranchRepository branchRepository;
    private final RestaurantRepository restaurantRepository;
    private final AuditLogMapper auditLogMapper;
    private final TenantGuard tenantGuard;

    /**
     * Ghi nhật ký.
     *
     * Hai quyết định thiết kế quan trọng:
     *
     * 1. `performedBy` lấy từ SecurityContext, KHÔNG nhận từ chỗ gọi — để không thể
     *    ghi nhật ký mạo danh người khác. Khách quét QR (không đăng nhập) → null,
     *    khi đó `create_by` của chính dòng log sẽ là "guest" (JpaAuditingConfig).
     *
     * 2. Bọc toàn bộ trong try/catch: nhật ký KHÔNG được làm fail nghiệp vụ chính.
     *    Nếu ghi log lỗi mà ném ra ngoài thì một lỗi phụ trợ sẽ rollback cả giao dịch
     *    thanh toán — hại hơn là mất một dòng log.
     *
     * 3. REQUIRES_NEW: dòng log commit trong transaction RIÊNG, không chung với nghiệp vụ.
     *    Lý do bắt buộc: có những chỗ ghi log rồi mới ném exception (ví dụ
     *    {@code InvoiceServiceImpl#issueEInvoice} ghi lại lần phát hành HĐĐT THẤT BẠI
     *    trước khi ném ConflictException). Nếu dùng chung transaction thì rollback sẽ
     *    xóa luôn dòng log — đúng lúc cần nó nhất.
     *    Đánh đổi: nếu nghiệp vụ rollback SAU khi đã ghi log, sẽ còn một dòng log của
     *    hành động không thành công. Chấp nhận được vì record() luôn được gọi ở cuối
     *    thao tác, và mất vết audit nguy hiểm hơn là có vết thừa.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        try {
            recordInTransaction(event);
        } catch (Exception e) {
            log.error(
                    "Không ghi được nhật ký cho {} #{} ({}): {}",
                    event.getEntityType(),
                    event.getEntityId(),
                    event.getActionType(),
                    e.getMessage(),
                    e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInTransaction(AuditEvent event) {
        // Ưu tiên SecurityContext; sự kiện đăng nhập thì chưa có nên nhận từ event.
        Account performedBy = currentAccountOrNull();
        if (performedBy == null && event.getPerformedByAccountId() != null) {
            performedBy = accountRepository.findById(event.getPerformedByAccountId()).orElse(null);
        }

        Long restaurantId =
                event.getRestaurantId() != null
                        ? event.getRestaurantId()
                        : (performedBy != null && performedBy.getRestaurant() != null
                                ? performedBy.getRestaurant().getId()
                                : null);

        Restaurant restaurant =
                restaurantId != null
                        ? restaurantRepository.findById(restaurantId).orElse(null)
                        : null;
        Branch branch =
                event.getBranchId() != null
                        ? branchRepository.findById(event.getBranchId()).orElse(null)
                        : null;
        Account approvedBy =
                event.getApprovedByAccountId() != null
                        ? accountRepository.findById(event.getApprovedByAccountId()).orElse(null)
                        : null;

        AuditLog entry =
                AuditLog.builder()
                        .restaurant(restaurant)
                        .branch(branch)
                        .performedBy(performedBy)
                        .approvedBy(approvedBy)
                        .occurredAt(LocalDateTime.now())
                        .entityType(event.getEntityType())
                        .entityId(event.getEntityId())
                        .actionType(event.getActionType())
                        .valueBefore(event.getValueBefore())
                        .valueAfter(event.getValueAfter())
                        .reason(event.getReason())
                        .build();

        auditLogRepository.save(entry);
    }

    private Account currentAccountOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || !(auth.getPrincipal() instanceof AccountUserDetails details)) {
            return null;
        }
        return accountRepository.findById(details.getAccountId()).orElse(null);
    }

    @Override
    public AuditLogResponse getAuditLogById(Long id) {
        AuditLog auditLog = findAuditLogById(id);
        tenantGuard.assertRestaurant(
                auditLog.getRestaurant() != null ? auditLog.getRestaurant().getId() : null);
        assertReadableBranch(auditLog);
        return auditLogMapper.toResponse(auditLog);
    }

    @Override
    public PageResponse<AuditLogResponse> getAllAuditLogs(
            Long restaurantId,
            Long branchId,
            String entityType,
            List<String> actionTypes,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        // Nhật ký chứa toàn bộ vết tiền/quyền — lọc theo nhà hàng khác phải bị chặn.
        tenantGuard.assertRestaurant(restaurantId);
        // BR-18 — chỉ định chi nhánh không được phân công ⇒ 403.
        tenantGuard.assertBranchAssigned(branchId, TenantGuard.ROLE_BRANCH_MANAGER);

        // Không có filter nào và cũng không phải Quản trị nền tảng → mặc định giới hạn
        // vào nhà hàng của người đang đăng nhập, thay vì findAll() toàn hệ thống.
        if (restaurantId == null && branchId == null && !tenantGuard.isPlatformAdmin()) {
            restaurantId = tenantGuard.currentRestaurantIdOrNull();
        }

        // BR-18 — KHÔNG truyền branchId thì vẫn phải giới hạn vào chi nhánh được phân
        // công. Thiếu nhánh này thì chỉ cần đổi sang `?restaurantId=` là đọc cả nhà hàng.
        List<Long> allowedBranchIds =
                tenantGuard.assignedBranchIdsOrNull(TenantGuard.ROLE_BRANCH_MANAGER);
        if (allowedBranchIds != null && allowedBranchIds.isEmpty()) {
            return emptyPage(pageable);
        }

        // Phạm vi chi nhánh: chỉ định branchId thì lọc đúng chi nhánh đó; không thì dùng
        // danh sách chi nhánh được phân công (BR-18). Cả hai đường đều đi qua BRANCH_SCOPE nên
        // dòng đăng nhập của nhân viên chi nhánh luôn được tính như nhau (be-issues 21, QĐ-11).
        List<Long> scopeBranchIds = branchId != null ? List.of(branchId) : allowedBranchIds;
        boolean filterByBranch = scopeBranchIds != null;

        boolean filterByDate = startDate != null && endDate != null;
        boolean filterByAction = actionTypes != null && !actionTypes.isEmpty();

        Page<AuditLog> auditLogPage =
                auditLogRepository.search(
                        restaurantId,
                        entityType,
                        filterByDate,
                        filterByDate ? startDate : EPOCH,
                        filterByDate ? endDate : EPOCH,
                        filterByBranch,
                        filterByBranch ? scopeBranchIds : NO_BRANCH_IDS,
                        filterByAction,
                        filterByAction ? actionTypes : NO_ACTION_TYPES,
                        pageable);

        List<AuditLogResponse> responses =
                auditLogPage.getContent().stream()
                        .map(auditLogMapper::toResponse)
                        .collect(Collectors.toList());

        return PageResponse.<AuditLogResponse>builder()
                .content(responses)
                .pageNumber(auditLogPage.getNumber())
                .pageSize(auditLogPage.getSize())
                .totalElements(auditLogPage.getTotalElements())
                .totalPages(auditLogPage.getTotalPages())
                .first(auditLogPage.isFirst())
                .last(auditLogPage.isLast())
                .build();
    }

    @Override
    public List<AuditLogResponse> getAuditLogsByRestaurant(Long restaurantId) {
        tenantGuard.assertRestaurant(restaurantId);
        // BR-18 — vai trò cấp chi nhánh gọi đường này thì vẫn chỉ thấy chi nhánh mình.
        List<Long> allowedBranchIds =
                tenantGuard.assignedBranchIdsOrNull(TenantGuard.ROLE_BRANCH_MANAGER);
        if (allowedBranchIds != null && allowedBranchIds.isEmpty()) {
            return List.of();
        }
        // be-issues mục 16: Pageable.unpaged() ở đây trả về RỖNG dù có dữ liệu.
        // Dùng truy vấn trả List trực tiếp, đã sắp mới-nhất-trước.
        List<AuditLog> auditLogs =
                allowedBranchIds == null
                        ? auditLogRepository.findByRestaurantIdOrderByOccurredAtDesc(restaurantId)
                        : auditLogRepository.findBranchScopedByRestaurantList(
                                restaurantId, allowedBranchIds);
        return auditLogs.stream().map(auditLogMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<AuditLogResponse> getAuditLogsByBranch(Long branchId) {
        // BR-18 — FR-LOG-01: "Quản lý CN chỉ xem nhật ký chi nhánh mình".
        tenantGuard.assertBranchAssigned(branchId, TenantGuard.ROLE_BRANCH_MANAGER);
        // Gồm cả dòng đăng nhập / đăng xuất của nhân viên có phân công ở chi nhánh này
        // (be-issues mục 21).
        return auditLogRepository.findBranchScopedList(List.of(branchId)).stream()
                .map(auditLogMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditLogResponse> getAuditLogsByEntityType(String entityType) {
        // Đường này trước đây KHÔNG có chốt nào: đọc được nhật ký của nhà hàng khác.
        Long restaurantId = tenantGuard.currentRestaurantIdOrNull();
        boolean platformAdmin = tenantGuard.isPlatformAdmin();
        List<Long> allowedBranchIds =
                tenantGuard.assignedBranchIdsOrNull(TenantGuard.ROLE_BRANCH_MANAGER);
        if (allowedBranchIds != null && allowedBranchIds.isEmpty()) {
            return List.of();
        }
        List<AuditLog> auditLogs =
                allowedBranchIds == null
                        ? auditLogRepository.findByEntityTypeOrderByOccurredAtDesc(entityType)
                        : auditLogRepository.findBranchScopedByEntityTypeList(
                                entityType, allowedBranchIds);
        return auditLogs.stream()
                .filter(
                        a ->
                                platformAdmin
                                        || restaurantId == null
                                        || (a.getRestaurant() != null
                                                && restaurantId.equals(a.getRestaurant().getId())))
                .map(auditLogMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditLogResponse> getAuditLogsByPerformedBy(Long accountId) {
        accountRepository
                .findById(accountId)
                .ifPresent(
                        acc ->
                                tenantGuard.assertRestaurant(
                                        acc.getRestaurant() != null
                                                ? acc.getRestaurant().getId()
                                                : null));
        List<Long> allowedBranchIds =
                tenantGuard.assignedBranchIdsOrNull(TenantGuard.ROLE_BRANCH_MANAGER);
        if (allowedBranchIds != null && allowedBranchIds.isEmpty()) {
            return List.of();
        }
        // be-issues mục 16 — xem ghi chú ở getAuditLogsByRestaurant.
        List<AuditLog> auditLogs =
                allowedBranchIds == null
                        ? auditLogRepository.findByPerformedByIdOrderByOccurredAtDesc(accountId)
                        : auditLogRepository.findBranchScopedByPerformedByList(
                                accountId, allowedBranchIds);
        return auditLogs.stream().map(auditLogMapper::toResponse).collect(Collectors.toList());
    }

    /**
     * BR-18 — vai trò cấp chi nhánh chỉ đọc được bản ghi của chi nhánh mình, CỘNG với dòng
     * đăng nhập / đăng xuất của nhân viên có phân công ở chi nhánh đó (be-issues mục 21).
     * Giữ đúng cùng phạm vi với truy vấn danh sách, để không có chuyện thấy dòng trong danh
     * sách mà mở chi tiết lại 403.
     */
    private void assertReadableBranch(AuditLog auditLog) {
        List<Long> allowedBranchIds =
                tenantGuard.assignedBranchIdsOrNull(TenantGuard.ROLE_BRANCH_MANAGER);
        if (allowedBranchIds == null) {
            return;
        }
        Long branchId = auditLog.getBranch() != null ? auditLog.getBranch().getId() : null;
        if (branchId != null && allowedBranchIds.contains(branchId)) {
            return;
        }
        if (isLoginEventOfAssignedStaff(auditLog, allowedBranchIds)) {
            return;
        }
        throw new AccessDeniedException("Bạn không được phân công ở chi nhánh này");
    }

    /** Dòng ĐĂNG NHẬP / ĐĂNG XUẤT của tài khoản có phân công ở một trong các chi nhánh cho phép. */
    private boolean isLoginEventOfAssignedStaff(AuditLog auditLog, List<Long> allowedBranchIds) {
        String action = auditLog.getActionType();
        if (!AuditAction.LOGIN.equals(action) && !AuditAction.LOGOUT.equals(action)) {
            return false;
        }
        Account performedBy = auditLog.getPerformedBy();
        if (performedBy == null) {
            return false;
        }
        return assignmentRepository.findByAccountId(performedBy.getId()).stream()
                .anyMatch(
                        a ->
                                a.getBranch() != null
                                        && allowedBranchIds.contains(a.getBranch().getId()));
    }

    /**
     * Giá trị "không dùng" cho các tham số bắt buộc phải khác null của truy vấn `search`.
     * JPQL không nhận danh sách rỗng trong `IN`, nên truyền danh sách một phần tử không khớp gì.
     */
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private static final List<Long> NO_BRANCH_IDS = List.of(-1L);
    private static final List<String> NO_ACTION_TYPES = List.of("");

    /** Trang rỗng — dùng khi người dùng chưa có phân công chi nhánh nào. */
    private PageResponse<AuditLogResponse> emptyPage(Pageable pageable) {
        return PageResponse.<AuditLogResponse>builder()
                .content(List.of())
                .pageNumber(pageable.isPaged() ? pageable.getPageNumber() : 0)
                .pageSize(pageable.isPaged() ? pageable.getPageSize() : 0)
                .totalElements(0L)
                .totalPages(0)
                .first(true)
                .last(true)
                .build();
    }

    private AuditLog findAuditLogById(Long id) {
        return auditLogRepository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Không tìm thấy nhật ký với ID: " + id));
    }
}
