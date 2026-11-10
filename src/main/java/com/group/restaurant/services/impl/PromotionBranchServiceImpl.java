package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.promotionBranch.PromotionBranchAddRequest;
import com.group.restaurant.dtos.response.promotionBranch.PromotionBranchResponse;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.PromotionBranchMapper;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Promotion;
import com.group.restaurant.models.PromotionBranch;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.PromotionBranchRepository;
import com.group.restaurant.repositories.PromotionRepository;
import com.group.restaurant.services.PromotionBranchService;
import com.group.restaurant.utils.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PromotionBranchServiceImpl implements PromotionBranchService {

    private final PromotionBranchRepository promotionBranchRepository;
    private final BranchRepository branchRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionBranchMapper promotionBranchMapper;
    private final TenantGuard tenantGuard;

    @Override
    public PromotionBranchResponse assignPromotionToBranch(PromotionBranchAddRequest request) {
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chi nhánh với ID: " + request.getBranchId()));

        Promotion promotion = promotionRepository.findById(request.getPromotionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khuyến mãi với ID: " + request.getPromotionId()));

        // Kiểm tra cùng nhà hàng
        if (!branch.getRestaurant().getId().equals(promotion.getRestaurant().getId())) {
            throw new ConflictException("Chi nhánh và khuyến mãi không cùng nhà hàng");
        }
        
        // Kiểm tra quyền
        tenantGuard.assertRestaurant(promotion.getRestaurant().getId());

        if (promotionBranchRepository.existsByBranchIdAndPromotionId(request.getBranchId(), request.getPromotionId())) {
            throw new ConflictException("Khuyến mãi này đã được áp dụng cho chi nhánh");
        }

        // Xóa bản ghi cũ nếu còn tồn tại trong DB để tránh lỗi duplicate constraint
        promotionBranchRepository.hardDeleteByBranchIdAndPromotionId(request.getBranchId(), request.getPromotionId());

        PromotionBranch promotionBranch = PromotionBranch.builder()
                .branch(branch)
                .promotion(promotion)
                .build();

        PromotionBranch saved = promotionBranchRepository.save(promotionBranch);
        return promotionBranchMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionBranchResponse> getByBranch(Long branchId) {
        tenantGuard.assertBranch(branchId);
        if (!branchRepository.existsById(branchId)) {
            throw new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + branchId);
        }

        return promotionBranchRepository.findByBranchId(branchId).stream()
                .map(promotionBranchMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionBranchResponse> getByPromotion(Long promotionId) {
        if (!promotionRepository.existsById(promotionId)) {
            throw new ResourceNotFoundException("Không tìm thấy khuyến mãi với ID: " + promotionId);
        }

        return promotionBranchRepository.findByPromotionId(promotionId).stream()
                .map(promotionBranchMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void removePromotionFromBranch(Long id) {
        if (!promotionBranchRepository.existsById(id)) {
            throw new ResourceNotFoundException(
                    "Không tìm thấy liên kết khuyến mãi - chi nhánh với ID: " + id);
        }

        promotionBranchRepository.hardDeleteById(id);
    }
}