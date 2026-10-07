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
import com.group.restaurant.services.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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

    @Override
    public BranchResponse createBranch(BranchAddRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhà hàng với ID: " + request.getRestaurantId()));

        if (branchRepository.findByRestaurantIdAndCode(request.getRestaurantId(), request.getCode()).isPresent()) {
            throw new ConflictException("Mã chi nhánh '" + request.getCode() + "' đã tồn tại trong nhà hàng này");
        }

        Branch branch = branchMapper.toEntity(request);
        branch.setRestaurant(restaurant);
        branch.setStatus(BranchStatus.ACTIVE);

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
        Branch branch = branchRepository.findByRestaurantIdAndCode(restaurantId, code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chi nhánh với mã: " + code + " trong nhà hàng ID: " + restaurantId));
        return buildBranchResponse(branch);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BranchListResponse> getAllBranches(
            Long restaurantId,
            BranchStatus status,
            Pageable pageable
    ) {
        List<Branch> branches;

        if (restaurantId != null && status != null) {
            branches = branchRepository.findByRestaurantIdAndStatus(restaurantId, status);
        } else if (restaurantId != null) {
            branches = branchRepository.findByRestaurantId(restaurantId);
        } else {
            Page<Branch> branchPage = branchRepository.findAll(pageable);
            List<BranchListResponse> responses = branchPage.getContent().stream()
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

        List<BranchListResponse> responses = branches.stream()
                .map(branchMapper::toListResponse)
                .collect(Collectors.toList());

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
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new ResourceNotFoundException("Không tìm thấy nhà hàng với ID: " + restaurantId);
        }

        List<Branch> branches = branchRepository.findByRestaurantId(restaurantId);
        return branches.stream()
                .map(branchMapper::toListResponse)
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
        dishOverrideRepository.findByBranchId(id).forEach(dishOverride -> {
            dishOverride.softDelete(null);
            dishOverrideRepository.save(dishOverride);
        });

        // Cascade soft delete: DiningTable
        diningTableRepository.findByBranchId(id).forEach(table -> {
            table.softDelete(null);
            diningTableRepository.save(table);
        });

        // Cascade soft delete: Area
        areaRepository.findByBranchId(id).forEach(area -> {
            area.softDelete(null);
            areaRepository.save(area);
        });

        branch.softDelete(null);
        branchRepository.save(branch);
    }

    @Override
    public BranchResponse updateManagerConfig(Long id, BranchManagerConfigRequest request) {
        Branch branch = findBranchById(id);

        if (request.getOpeningTime() != null) branch.setOpeningTime(request.getOpeningTime());
        if (request.getClosingTime() != null) branch.setClosingTime(request.getClosingTime());
        if (request.getBusinessDayStartTime() != null) branch.setBusinessDayStartTime(request.getBusinessDayStartTime());
        if (request.getBankAccountNo() != null) branch.setBankAccountNo(request.getBankAccountNo());
        if (request.getBankName() != null) branch.setBankName(request.getBankName());
        if (request.getPendingConfirmTimeoutMinutes() != null) branch.setPendingConfirmTimeoutMinutes(request.getPendingConfirmTimeoutMinutes());
        if (request.getReservationHoldMinutes() != null) branch.setReservationHoldMinutes(request.getReservationHoldMinutes());

        return buildBranchResponse(branchRepository.save(branch));
    }

    @Override
    public BranchResponse updateAdminConfig(Long id, BranchAdminConfigRequest request) {
        Branch branch = findBranchById(id);

        boolean vatOrFeeChanging = (request.getVatRate() != null || request.getServiceFeeRate() != null);
        int openSessionCount = 0;

        if (vatOrFeeChanging) {
            // BR-08: cảnh báo khi đổi VAT/phí dịch vụ mà còn phiên ĐANG PHỤC VỤ hoặc CHỜ THANH TOÁN
            openSessionCount = (int) tableSessionRepository.findActiveSessionsByBranchId(id)
                    .stream()
                    .filter(s -> s.getStatus() == com.group.restaurant.enums.TableSessionStatus.SERVING
                            || s.getStatus() == com.group.restaurant.enums.TableSessionStatus.WAITING_FOR_PAY)
                    .count();
        }

        if (request.getVatRate() != null) branch.setVatRate(request.getVatRate());
        if (request.getServiceFeeRate() != null) branch.setServiceFeeRate(request.getServiceFeeRate());
        if (request.getVatAppliedOnServiceFee() != null) branch.setVatAppliedOnServiceFee(request.getVatAppliedOnServiceFee());
        if (request.getDiscountApprovalThreshold() != null) branch.setDiscountApprovalThreshold(request.getDiscountApprovalThreshold());
        if (request.getTaxCode() != null) branch.setTaxCode(request.getTaxCode());

        BranchResponse response = buildBranchResponse(branchRepository.save(branch));
        if (openSessionCount > 0) {
            response.setHasOpenSessionWarning(true);
            response.setOpenSessionCount(openSessionCount);
        }
        return response;
    }

    @Override
    public BranchResponse copyConfigFromBranch(Long targetBranchId, BranchCopyConfigRequest request) {
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

    private Branch findBranchById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + id));
    }

    private BranchResponse buildBranchResponse(Branch branch) {
        BranchResponse response = branchMapper.toResponse(branch);
        response.setTableCount(0);
        response.setActiveSessionCount(0);
        return response;
    }
}
