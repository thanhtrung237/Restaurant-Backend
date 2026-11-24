package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.branch.BranchAddRequest;
import com.group.restaurant.dtos.request.branch.BranchAdminConfigRequest;
import com.group.restaurant.dtos.request.branch.BranchCopyConfigRequest;
import com.group.restaurant.dtos.request.branch.BranchManagerConfigRequest;
import com.group.restaurant.dtos.request.branch.BranchUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.branch.BranchListResponse;
import com.group.restaurant.dtos.response.branch.BranchResponse;
import com.group.restaurant.enums.BranchStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.BranchMapper;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.repositories.AreaRepository;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.DiningTableRepository;
import com.group.restaurant.repositories.DishOverrideRepository;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.repositories.TableSessionRepository;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.BranchService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.services.guest.GeofenceService;
import com.group.restaurant.utils.TenantGuard;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;
    private final RestaurantRepository restaurantRepository;
    private final AreaRepository areaRepository;
    private final DiningTableRepository diningTableRepository;
    private final DishOverrideRepository dishOverrideRepository;
    private final TableSessionRepository tableSessionRepository;
    private final BranchMapper branchMapper;
    private final AuditLogService auditLogService;
    private final TenantGuard tenantGuard;

    @Override
    public BranchResponse createBranch(BranchAddRequest request) {
        tenantGuard.assertRestaurant(request.getRestaurantId());
        Restaurant restaurant =
                restaurantRepository
                        .findById(request.getRestaurantId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy nhà hàng với ID: "
                                                        + request.getRestaurantId()));

        if (branchRepository
                .findByRestaurantIdAndCode(request.getRestaurantId(), request.getCode())
                .isPresent()) {
            throw new ConflictException(
                    "Mã chi nhánh '" + request.getCode() + "' đã tồn tại trong nhà hàng này");
        }

        Branch branch = branchMapper.toEntity(request);
        branch.setRestaurant(restaurant);
        branch.setStatus(BranchStatus.ACTIVE);
        // QĐ-17 (câu #7): phí dịch vụ CHỊU VAT — giá tính thuế gồm cả khoản phụ thu/phí thu thêm
        // (TT 219/2013 Điều 7). `BranchAddRequest` không có trường này, nên đặt mặc định ở đây thay
        // vì sửa giá trị khởi tạo trong @Entity. Chi nhánh đã tồn tại KHÔNG bị đụng tới.
        branch.setVatAppliedOnServiceFee(Boolean.TRUE);

        Branch savedBranch = branchRepository.save(branch);
        return buildBranchResponse(savedBranch);
    }

    @Override
    public BranchResponse updateBranch(Long id, BranchUpdateRequest request) {
        Branch branch = findBranchById(id);

        branchMapper.updateEntity(branch, request);
        Branch updatedBranch = branchRepository.save(branch);

        return buildBranchResponse(updatedBranch);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponse getBranchById(Long id) {
        Branch branch = findBranchById(id);
        return buildBranchResponse(branch);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponse getBranchByCode(Long restaurantId, String code) {
        tenantGuard.assertRestaurant(restaurantId);
        Branch branch =
                branchRepository
                        .findByRestaurantIdAndCode(restaurantId, code)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy chi nhánh với mã: "
                                                        + code
                                                        + " trong nhà hàng ID: "
                                                        + restaurantId));
        return buildBranchResponse(branch);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BranchListResponse> getAllBranches(
            Long restaurantId, BranchStatus status, Pageable pageable) {
        tenantGuard.assertRestaurant(restaurantId);
        // Bỏ trống restaurantId thì nhánh cuối gọi findAll() — trả dữ liệu MỌI nhà hàng.
        // Mặc định giới hạn vào nhà hàng của người đăng nhập; chỉ Quản trị nền tảng
        // được xem xuyên nhà hàng.
        if (restaurantId == null && !tenantGuard.isPlatformAdmin()) {
            restaurantId = tenantGuard.currentRestaurantIdOrNull();
        }

        List<Branch> branches;

        if (restaurantId != null && status != null) {
            branches = branchRepository.findByRestaurantIdAndStatus(restaurantId, status);
        } else if (restaurantId != null) {
            branches = branchRepository.findByRestaurantId(restaurantId);
        } else {
            Page<Branch> branchPage = branchRepository.findAll(pageable);
            List<BranchListResponse> responses =
                    branchPage.getContent().stream()
                            .map(branchMapper::toListResponse)
                            .collect(Collectors.toList());

            return PageResponse.<BranchListResponse>builder()
                    .content(responses)
                    .pageNumber(branchPage.getNumber())
                    .pageSize(branchPage.getSize())
                    .totalElements(branchPage.getTotalElements())
                    .totalPages(branchPage.getTotalPages())
                    .first(branchPage.isFirst())
                    .last(branchPage.isLast())
                    .build();
        }

        List<BranchListResponse> responses =
                branches.stream().map(branchMapper::toListResponse).collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), responses.size());
        List<BranchListResponse> pageContent = responses.subList(start, end);

        Page<BranchListResponse> page = new PageImpl<>(pageContent, pageable, responses.size());

        return PageResponse.<BranchListResponse>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchListResponse> getBranchesByRestaurant(Long restaurantId) {
        tenantGuard.assertRestaurant(restaurantId);
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new ResourceNotFoundException("Không tìm thấy nhà hàng với ID: " + restaurantId);
        }

        List<Branch> branches = branchRepository.findByRestaurantId(restaurantId);
        return branches.stream()
                .map(
                        branch -> {
                            BranchListResponse response = branchMapper.toListResponse(branch);
                            enrichBranchListResponse(response, branch);
                            return response;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public void changeStatus(Long id, BranchStatus status) {
        Branch branch = findBranchById(id);
        branch.setStatus(status);
        branchRepository.save(branch);
    }

    @Override
    public void deleteBranch(Long id) {
        Branch branch = findBranchById(id);

        // Cascade soft delete: DishOverride
        dishOverrideRepository
                .findByBranchId(id)
                .forEach(
                        dishOverride -> {
                            dishOverride.softDelete(null);
                            dishOverrideRepository.save(dishOverride);
                        });

        // Cascade soft delete: DiningTable
        diningTableRepository
                .findByBranchId(id)
                .forEach(
                        table -> {
                            table.softDelete(null);
                            diningTableRepository.save(table);
                        });

        // Cascade soft delete: Area
        areaRepository
                .findByBranchId(id)
                .forEach(
                        area -> {
                            area.softDelete(null);
                            areaRepository.save(area);
                        });

        branch.softDelete(null);
        branchRepository.save(branch);
    }

    @Override
    public BranchResponse updateManagerConfig(Long id, BranchManagerConfigRequest request) {
        Branch branch = findBranchById(id);
        String configBefore = managerConfigSnapshot(branch);

        if (request.getOpeningTime() != null) branch.setOpeningTime(request.getOpeningTime());
        if (request.getClosingTime() != null) branch.setClosingTime(request.getClosingTime());
        // 3 field dưới đây XÓA TRẮNG được: gửi null (hoặc chuỗi rỗng) là gỡ cấu hình.
        // "Không gửi field" vẫn giữ nguyên giá trị cũ — xem BranchManagerConfigRequest.
        if (request.isBusinessDayStartTimeProvided()) {
            branch.setBusinessDayStartTime(request.getBusinessDayStartTime());
        }
        if (request.isBankAccountNoProvided()) {
            branch.setBankAccountNo(blankToNull(request.getBankAccountNo()));
        }
        if (request.isBankNameProvided()) {
            branch.setBankName(blankToNull(request.getBankName()));
        }
        if (request.getPendingConfirmTimeoutMinutes() != null)
            branch.setPendingConfirmTimeoutMinutes(request.getPendingConfirmTimeoutMinutes());
        if (request.getReservationHoldMinutes() != null)
            branch.setReservationHoldMinutes(request.getReservationHoldMinutes());

        BranchResponse managerConfigResponse = buildBranchResponse(branchRepository.save(branch));

        auditLogService.record(
                AuditEvent.builder()
                        .entityType("Branch")
                        .entityId(branch.getId())
                        .actionType(AuditAction.CONFIG_CHANGE)
                        .branchId(branch.getId())
                        .valueBefore(configBefore)
                        .valueAfter(managerConfigSnapshot(branch))
                        .reason("QL chi nhánh đổi cấu hình vận hành chi nhánh")
                        .build());

        return managerConfigResponse;
    }

    @Override
    public BranchResponse updateAdminConfig(Long id, BranchAdminConfigRequest request) {
        Branch branch = findBranchById(id);
        String configBefore = adminConfigSnapshot(branch);
        if (request.getLatitude() != null) branch.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) branch.setLongitude(request.getLongitude());
        if (request.getGeofenceEnabled() != null)
            branch.setGeofenceEnabled(request.getGeofenceEnabled());
        if (request.getGeofenceRadiusMeters() != null)
            branch.setGeofenceRadiusMeters(request.getGeofenceRadiusMeters());
        if (request.getGeofenceMaxAccuracyMeters() != null)
            branch.setGeofenceMaxAccuracyMeters(request.getGeofenceMaxAccuracyMeters());
        GeofenceService.validateConfiguration(branch);

        boolean vatOrFeeChanging =
                (request.getVatRate() != null || request.getServiceFeeRate() != null);
        int openSessionCount = 0;

        if (vatOrFeeChanging) {
            // BR-08: cảnh báo khi đổi VAT/phí dịch vụ mà còn phiên ĐANG PHỤC VỤ hoặc CHỜ THANH TOÁN
            openSessionCount =
                    (int)
                            tableSessionRepository.findActiveSessionsByBranchId(id).stream()
                                    .filter(
                                            s ->
                                                    s.getStatus()
                                                                    == com.group.restaurant.enums
                                                                            .TableSessionStatus
                                                                            .SERVING
                                                            || s.getStatus()
                                                                    == com.group.restaurant.enums
                                                                            .TableSessionStatus
                                                                            .WAITING_FOR_PAY)
                                    .count();
        }

        if (request.getVatRate() != null) branch.setVatRate(request.getVatRate());
        if (request.getServiceFeeRate() != null)
            branch.setServiceFeeRate(request.getServiceFeeRate());
        if (request.getVatAppliedOnServiceFee() != null)
            branch.setVatAppliedOnServiceFee(request.getVatAppliedOnServiceFee());
        if (request.getDiscountApprovalThreshold() != null)
            branch.setDiscountApprovalThreshold(request.getDiscountApprovalThreshold());
        if (request.getTaxCode() != null) branch.setTaxCode(request.getTaxCode());

        BranchResponse response = buildBranchResponse(branchRepository.save(branch));
        if (openSessionCount > 0) {
            response.setHasOpenSessionWarning(true);
            response.setOpenSessionCount(openSessionCount);
        }

        // VAT / phí dịch vụ / ngưỡng duyệt chiết khấu / MST đều ảnh hưởng trực tiếp tới
        // tiền khách phải trả — đổi ở đây bắt buộc có vết (FR-LOG-01).
        auditLogService.record(
                AuditEvent.builder()
                        .entityType("Branch")
                        .entityId(branch.getId())
                        .actionType(AuditAction.CONFIG_CHANGE)
                        .branchId(branch.getId())
                        .valueBefore(configBefore)
                        .valueAfter(adminConfigSnapshot(branch))
                        .reason(
                                openSessionCount > 0
                                        ? "Quản trị NH đổi cấu hình thuế/phí khi còn "
                                                + openSessionCount
                                                + " phiên đang mở (BR-08)"
                                        : "Quản trị NH đổi cấu hình thuế/phí chi nhánh")
                        .build());

        return response;
    }

    /** Ảnh cấu hình cấp QL chi nhánh, để so sánh trước/sau trong nhật ký. */
    private String managerConfigSnapshot(Branch b) {
        return "openingTime="
                + b.getOpeningTime()
                + "; closingTime="
                + b.getClosingTime()
                + "; businessDayStartTime="
                + b.getBusinessDayStartTime()
                + "; bankName="
                + b.getBankName()
                + "; bankAccountNo="
                + b.getBankAccountNo()
                + "; pendingConfirmTimeoutMinutes="
                + b.getPendingConfirmTimeoutMinutes()
                + "; reservationHoldMinutes="
                + b.getReservationHoldMinutes();
    }

    /** Ảnh cấu hình cấp Quản trị nhà hàng (thuế/phí/ngưỡng duyệt). */
    private String adminConfigSnapshot(Branch b) {
        return "geofenceEnabled="
                + b.isGeofenceEnabled()
                + "; latitude="
                + b.getLatitude()
                + "; longitude="
                + b.getLongitude()
                + "; radius="
                + b.getGeofenceRadiusMeters()
                + "; maxAccuracy="
                + b.getGeofenceMaxAccuracyMeters()
                + "; vatRate="
                + b.getVatRate()
                + "; serviceFeeRate="
                + b.getServiceFeeRate()
                + "; vatAppliedOnServiceFee="
                + b.getVatAppliedOnServiceFee()
                + "; discountApprovalThreshold="
                + b.getDiscountApprovalThreshold()
                + "; taxCode="
                + b.getTaxCode();
    }

    @Override
    public BranchResponse copyConfigFromBranch(
            Long targetBranchId, BranchCopyConfigRequest request) {
        Branch target = findBranchById(targetBranchId);
        Branch source = findBranchById(request.getSourceBranchId());

        if (request.isCopyOpeningHours()) {
            target.setOpeningTime(source.getOpeningTime());
            target.setClosingTime(source.getClosingTime());
            target.setBusinessDayStartTime(source.getBusinessDayStartTime());
            target.setPendingConfirmTimeoutMinutes(source.getPendingConfirmTimeoutMinutes());
            target.setReservationHoldMinutes(source.getReservationHoldMinutes());
        }

        if (request.isCopyFeeRates()) {
            target.setVatRate(source.getVatRate());
            target.setServiceFeeRate(source.getServiceFeeRate());
            target.setDiscountApprovalThreshold(source.getDiscountApprovalThreshold());
        }

        if (request.isCopyBankInfo()) {
            target.setBankAccountNo(source.getBankAccountNo());
            target.setBankName(source.getBankName());
        }

        return buildBranchResponse(branchRepository.save(target));
    }

    /**
     * Mọi thao tác trên 1 chi nhánh đều đi qua đây, nên đặt chốt tenant tại chỗ này
     * phủ được cả updateBranch / getBranchById / changeStatus / deleteBranch /
     * updateManagerConfig / updateAdminConfig / copyConfigFromBranch — thay vì rải
     * lời gọi guard ra 7 method và rủi ro bỏ sót method thêm sau này.
     */
    /** Chuỗi rỗng và null đều là "chưa cấu hình" — lưu thống nhất thành null. */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Branch findBranchById(Long id) {
        Branch branch =
                branchRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy chi nhánh với ID: " + id));
        tenantGuard.assertBranch(branch);
        return branch;
    }

    private BranchResponse buildBranchResponse(Branch branch) {
        BranchResponse response = branchMapper.toResponse(branch);
        response.setTableCount(0);
        response.setActiveSessionCount(0);
        return response;
    }

    /**
     * Enrich BranchListResponse với tableCount và managerName
     */
    private void enrichBranchListResponse(BranchListResponse response, Branch branch) {
        // Set table count
        long tableCount = diningTableRepository.countByBranchId(branch.getId());
        response.setTableCount((int) tableCount);

        // Set manager name - TODO: cần join với Account/Assignment để lấy tên người quản lý
        // Hiện tại để null, frontend sẽ hiển thị "Chưa phân công"
        response.setManagerName(null);
    }
}
