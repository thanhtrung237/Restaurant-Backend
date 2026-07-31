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

    @Override
    public PromotionBranchResponse assignPromotionToBranch(PromotionBranchAddRequest request) {
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chi nhánh với ID: " + request.getBranchId()));

        Promotion promotion = promotionRepository.findById(request.getPromotionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khuyến mãi với ID: " + request.getPromotionId()));

        if (promotionBranchRepository.existsByBranchIdAndPromotionId(request.getBranchId(), request.getPromotionId())) {
            throw new ConflictException(
                    "Khuyến mãi đã được gán cho chi nhánh này");
        }

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
        return promotionBranchRepository.findByBranchId(branchId)
                .stream()
                .map(promotionBranchMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionBranchResponse> getByPromotion(Long promotionId) {
        return promotionBranchRepository.findByPromotionId(promotionId)
                .stream()
                .map(promotionBranchMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void removePromotionFromBranch(Long id) {
        PromotionBranch promotionBranch = promotionBranchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy promotion-branch với ID: " + id));
        promotionBranchRepository.delete(promotionBranch);
    }
}
