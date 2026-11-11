package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.promotion.PromotionAddRequest;
import com.group.restaurant.dtos.request.promotion.PromotionUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.promotion.PromotionResponse;
import com.group.restaurant.enums.PromotionStatus;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.PromotionMapper;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Promotion;
import com.group.restaurant.models.PromotionBranch;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.PromotionBranchRepository;
import com.group.restaurant.repositories.PromotionRepository;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.PromotionService;
import com.group.restaurant.services.promotion.PromotionEngine;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.utils.TenantGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionBranchRepository promotionBranchRepository;
    private final BranchRepository branchRepository;
    private final RestaurantRepository restaurantRepository;
    private final PromotionMapper promotionMapper;
    private final AuditLogService auditLogService;
    private final TenantGuard tenantGuard;
    private final ObjectMapper objectMapper;
    private final PromotionEngine promotionEngine;

    @Override
    public PromotionResponse createPromotion(PromotionAddRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhà hàng với ID: " + request.getRestaurantId()));
        
        tenantGuard.assertRestaurant(request.getRestaurantId());

        // Validate ngày
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new InvalidParamException("Ngày kết thúc phải sau ngày bắt đầu");
        }

        // Validate JSON condition và action
        validateJson(request.getCondition(), "condition");
        validateJson(request.getAction(), "action");

        // Kiểm tra mã code duy nhất trong nhà hàng (không phải toàn hệ thống)
        if (request.getCode() != null && !request.getCode().isBlank()) {
            promotionRepository.findByRestaurantIdAndCode(request.getRestaurantId(), request.getCode())
                    .ifPresent(p -> {
                        throw new ConflictException("Mã khuyến mãi '" + request.getCode() + "' đã tồn tại trong nhà hàng");
                    });
        }

        Promotion promotion = promotionMapper.toEntity(request);
        promotion.setRestaurant(restaurant);
        
        // Trạng thái theo flag isActiveNow
        promotion.setStatus(Boolean.TRUE.equals(request.getIsActiveNow()) ? PromotionStatus.ACTIVE : PromotionStatus.INACTIVE);
        
        if (promotion.getPriority() == null) {
            promotion.setPriority(0);
        }
        if (promotion.getUsageLimit() != null && promotion.getUsageLimit() < 0) {
            throw new InvalidParamException("Giới hạn lượt sử dụng không được âm");
        }
        
        promotion.setAllBranches(Boolean.TRUE.equals(request.getAllBranches()));

        Promotion saved = promotionRepository.save(promotion);

        // Gán chi nhánh trong cùng transaction
        if (Boolean.TRUE.equals(request.getAllBranches())) {
            // Toàn hệ thống: gán cho tất cả chi nhánh hiện có của nhà hàng
            List<Branch> branches = branchRepository.findByRestaurantId(request.getRestaurantId());
            for (Branch branch : branches) {
                PromotionBranch pb = PromotionBranch.builder()
                        .promotion(saved)
                        .branch(branch)
                        .build();
                promotionBranchRepository.save(pb);
            }
        } else if (request.getBranchIds() != null && !request.getBranchIds().isEmpty()) {
            // Gán chi nhánh cụ thể
            for (Long branchId : request.getBranchIds()) {
                Branch branch = branchRepository.findById(branchId)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + branchId));
                
                // Kiểm tra chi nhánh cùng nhà hàng
                if (!branch.getRestaurant().getId().equals(request.getRestaurantId())) {
                    throw new InvalidParamException("Chi nhánh " + branchId + " không thuộc nhà hàng này");
                }
                
                PromotionBranch pb = PromotionBranch.builder()
                        .promotion(saved)
                        .branch(branch)
                        .build();
                promotionBranchRepository.save(pb);
            }
        }

        // Ghi nhật ký (FR-PRO-01)
        auditLogService.record(AuditEvent.builder()
                .entityType("Promotion")
                .entityId(saved.getId())
                .actionType(AuditAction.CREATE)
                .restaurantId(request.getRestaurantId())
                .valueAfter(String.format("Tạo KM '%s' (code=%s, cấp=%s, ưu tiên=%d)",
                        saved.getName(), saved.getCode(), saved.getApplyLevel(), saved.getPriority()))
                .build());

        return promotionMapper.toResponse(saved);
    }

    @Override
    public PromotionResponse updatePromotion(Long id, PromotionUpdateRequest request) {
        Promotion promotion = findById(id);
        
        String oldValue = String.format("Tên='%s', trạng thái=%s, ưu tiên=%d, cấp=%s, ngày=%s->%s, điều kiện=%s, hành động=%s, giới hạn=%s",
                promotion.getName(), promotion.getStatus(), promotion.getPriority(), promotion.getApplyLevel(),
                promotion.getStartDate(), promotion.getEndDate(), promotion.getCondition(), promotion.getAction(),
                promotion.getUsageLimit());

        // Validate ngày nếu có thay đổi
        LocalDate newStartDate = request.getStartDate() != null ? request.getStartDate() : promotion.getStartDate();
        LocalDate newEndDate = request.getEndDate() != null ? request.getEndDate() : promotion.getEndDate();
        if (newEndDate.isBefore(newStartDate)) {
            throw new InvalidParamException("Ngày kết thúc phải sau ngày bắt đầu");
        }

        // Validate JSON
        validateJson(request.getCondition(), "condition");
        validateJson(request.getAction(), "action");

        // Kiểm tra mã code nếu có thay đổi
        if (request.getCode() != null && !request.getCode().isBlank()) {
            promotionRepository.findByRestaurantIdAndCode(
                    promotion.getRestaurant().getId(), request.getCode())
                    .ifPresent(p -> {
                        if (!p.getId().equals(id)) {
                            throw new ConflictException("Mã khuyến mãi '" + request.getCode() + "' đã tồn tại trong nhà hàng");
                        }
                    });
        }

        if (request.getUsageLimit() != null && request.getUsageLimit() < promotion.getUsageCount()) {
            throw new InvalidParamException("Giới hạn lượt không được nhỏ hơn số lượt đã sử dụng (" + promotion.getUsageCount() + ")");
        }

        promotionMapper.updateEntity(promotion, request);

        // Cho phép xóa trắng mã code (để chuyển thành khuyến mãi tự động áp dụng)
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            promotion.setCode(null);
        } else {
            promotion.setCode(request.getCode().trim().toUpperCase());
        }

        // Cho phép xóa trắng mô tả
        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            promotion.setDescription(null);
        } else {
            promotion.setDescription(request.getDescription().trim());
        }

        // Cho phép gỡ bỏ giới hạn lượt dùng (null = không giới hạn)
        promotion.setUsageLimit(request.getUsageLimit());

        // Cho phép gỡ bỏ khung giờ và ngày trong tuần
        if (request.getTimeWindow() == null || request.getTimeWindow().trim().isEmpty()) {
            promotion.setTimeWindow(null);
        }
        if (request.getDaysOfWeek() == null || request.getDaysOfWeek().trim().isEmpty()) {
            promotion.setDaysOfWeek(null);
        }
        
        // Xử lý thay đổi allBranches và branchIds
        if (request.getAllBranches() != null) {
            promotion.setAllBranches(request.getAllBranches());
            
            // Xóa sạch toàn bộ gán cũ (hard delete native để tránh lỗi trùng lặp uk_promotion_branch)
            promotionBranchRepository.hardDeleteByPromotionId(id);

            if (request.getAllBranches()) {
                // Toàn hệ thống: gán cho tất cả chi nhánh hiện có của nhà hàng
                List<Branch> branches = branchRepository.findByRestaurantId(promotion.getRestaurant().getId());
                for (Branch branch : branches) {
                    PromotionBranch pb = PromotionBranch.builder()
                            .promotion(promotion)
                            .branch(branch)
                            .build();
                    promotionBranchRepository.save(pb);
                }
            } else if (request.getBranchIds() != null && !request.getBranchIds().isEmpty()) {
                // Gán chi nhánh cụ thể
                for (Long branchId : request.getBranchIds()) {
                    Branch branch = branchRepository.findById(branchId)
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + branchId));
                    
                    if (!branch.getRestaurant().getId().equals(promotion.getRestaurant().getId())) {
                        throw new InvalidParamException("Chi nhánh " + branchId + " không thuộc nhà hàng này");
                    }
                    
                    PromotionBranch pb = PromotionBranch.builder()
                            .promotion(promotion)
                            .branch(branch)
                            .build();
                    promotionBranchRepository.save(pb);
                }
            }
        } else if (request.getBranchIds() != null && !promotion.isAllBranches()) {
            // Chỉ cập nhật branchIds (không đổi allBranches)
            promotionBranchRepository.hardDeleteByPromotionId(id);
            
            for (Long branchId : request.getBranchIds()) {
                Branch branch = branchRepository.findById(branchId)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + branchId));
                
                if (!branch.getRestaurant().getId().equals(promotion.getRestaurant().getId())) {
                    throw new InvalidParamException("Chi nhánh " + branchId + " không thuộc nhà hàng này");
                }
                
                PromotionBranch pb = PromotionBranch.builder()
                        .promotion(promotion)
                        .branch(branch)
                        .build();
                promotionBranchRepository.save(pb);
            }
        }

        Promotion updated = promotionRepository.save(promotion);
        
        String newValue = String.format("Tên='%s', trạng thái=%s, ưu tiên=%d, cấp=%s, ngày=%s->%s, điều kiện=%s, hành động=%s, giới hạn=%s",
                updated.getName(), updated.getStatus(), updated.getPriority(), updated.getApplyLevel(),
                updated.getStartDate(), updated.getEndDate(), updated.getCondition(), updated.getAction(),
                updated.getUsageLimit());

        // Ghi nhật ký
        auditLogService.record(AuditEvent.builder()
                .entityType("Promotion")
                .entityId(updated.getId())
                .actionType(AuditAction.UPDATE)
                .restaurantId(updated.getRestaurant().getId())
                .valueBefore(oldValue)
                .valueAfter(newValue)
                .build());

        return promotionMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getPromotionById(Long id) {
        Promotion promotion = findById(id);
        PromotionResponse response = promotionMapper.toResponse(promotion);
        
        // Lấy danh sách branchIds
        List<Long> branchIds = promotionBranchRepository.findByPromotionId(id).stream()
                .map(pb -> pb.getBranch().getId())
                .collect(Collectors.toList());
        response.setBranchIds(branchIds);
        response.setAllBranches(promotion.isAllBranches());
        
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> getPromotionsByRestaurant(Long restaurantId, PromotionStatus status, Pageable pageable) {
        tenantGuard.assertRestaurant(restaurantId);
        List<Promotion> promotions;

        if (status != null) {
            promotions = promotionRepository.findByRestaurantIdAndStatus(restaurantId, status);
        } else {
            promotions = promotionRepository.findByRestaurantId(restaurantId);
        }

        List<PromotionResponse> responses = promotions.stream()
                .map(p -> {
                    PromotionResponse response = promotionMapper.toResponse(p);
                    // Lấy branchIds cho từng promotion
                    List<Long> branchIds = promotionBranchRepository.findByPromotionId(p.getId()).stream()
                            .map(pb -> pb.getBranch().getId())
                            .collect(Collectors.toList());
                    response.setBranchIds(branchIds);
                    response.setAllBranches(p.isAllBranches());
                    return response;
                })
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), responses.size());
        List<PromotionResponse> pageContent = responses.subList(start, end);

        Page<PromotionResponse> page = new PageImpl<>(pageContent, pageable, responses.size());

        return PageResponse.<PromotionResponse>builder()
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
    public void changeStatus(Long id, PromotionStatus status) {
        Promotion promotion = findById(id);
        PromotionStatus oldStatus = promotion.getStatus();
        promotion.setStatus(status);
        promotionRepository.save(promotion);
        
        // Ghi nhật ký
        auditLogService.record(AuditEvent.builder()
                .entityType("Promotion")
                .entityId(id)
                .actionType(AuditAction.STATUS_CHANGE)
                .restaurantId(promotion.getRestaurant().getId())
                .valueBefore(oldStatus.name())
                .valueAfter(status.name())
                .build());
    }

    @Override
    public void deletePromotion(Long id) {
        Promotion promotion = findById(id);

        if (promotion.getUsageCount() > 0) {
            throw new InvalidParamException("Khuyến mãi đã có " + promotion.getUsageCount() + " lượt sử dụng không thể xóa, vui lòng chuyển trạng thái sang ngưng hoạt động");
        }

        // Cascade hard delete liên kết chi nhánh
        promotionBranchRepository.hardDeleteByPromotionId(id);

        promotion.softDelete(null);
        promotionRepository.save(promotion);
        
        // Ghi nhật ký
        auditLogService.record(AuditEvent.builder()
                .entityType("Promotion")
                .entityId(id)
                .actionType(AuditAction.DELETE)
                .restaurantId(promotion.getRestaurant().getId())
                .valueBefore(String.format("Xóa KM '%s' (code=%s)", promotion.getName(), promotion.getCode()))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionResponse> getActivePromotionsByBranch(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + branchId));
        Long restaurantId = branch.getRestaurant().getId();

        List<Promotion> activePromotions = promotionEngine.getActivePromotions(branchId, restaurantId);
        return activePromotions.stream()
                .map(p -> {
                    PromotionResponse res = promotionMapper.toResponse(p);
                    res.setAllBranches(p.isAllBranches());
                    return res;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getPromotionByBranchAndCode(Long branchId, String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new InvalidParamException("Vui lòng nhập mã khuyến mãi");
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + branchId));
        Long restaurantId = branch.getRestaurant().getId();

        Promotion promotion = promotionRepository.findByRestaurantIdAndCode(restaurantId, code.trim().toUpperCase())
                .orElseGet(() -> promotionRepository.findByRestaurantIdAndCode(restaurantId, code.trim())
                        .orElseThrow(() -> new ResourceNotFoundException("Mã khuyến mãi '" + code.trim() + "' không tồn tại")));

        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            throw new InvalidParamException("Mã khuyến mãi '" + code.trim() + "' đang tạm dừng hoặc chưa kích hoạt");
        }

        LocalDate today = LocalDate.now();
        if (today.isBefore(promotion.getStartDate())) {
            throw new InvalidParamException("Chương trình khuyến mãi chưa bắt đầu (áp dụng từ " + promotion.getStartDate() + ")");
        }
        if (today.isAfter(promotion.getEndDate())) {
            throw new InvalidParamException("Mã khuyến mãi đã hết hạn vào ngày " + promotion.getEndDate());
        }

        if (!promotion.isAllBranches()) {
            boolean belongsToBranch = promotionBranchRepository.existsByBranchIdAndPromotionId(branchId, promotion.getId());
            if (!belongsToBranch) {
                throw new InvalidParamException("Mã khuyến mãi không áp dụng cho chi nhánh này");
            }
        }

        if (promotion.getUsageLimit() != null && promotion.getUsageCount() >= promotion.getUsageLimit()) {
            throw new InvalidParamException("Mã khuyến mãi đã hết lượt sử dụng");
        }

        PromotionResponse response = promotionMapper.toResponse(promotion);
        response.setAllBranches(promotion.isAllBranches());
        return response;
    }

    /** Chốt tenant tại finder: phủ getPromotionById / updatePromotion / changeStatus / deletePromotion. */
    private Promotion findById(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khuyến mãi với ID: " + id));
        tenantGuard.assertRestaurant(
                promotion.getRestaurant() != null ? promotion.getRestaurant().getId() : null);
        return promotion;
    }
    
    /** Kiểm tra JSON hợp lệ */
    private void validateJson(String json, String fieldName) {
        if (json != null && !json.isBlank()) {
            try {
                objectMapper.readTree(json);
            } catch (Exception e) {
                throw new InvalidParamException("Trường " + fieldName + " không phải JSON hợp lệ: " + e.getMessage());
            }
        }
    }
}
