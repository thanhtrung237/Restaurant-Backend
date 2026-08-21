package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.promotion.PromotionAddRequest;
import com.group.restaurant.dtos.request.promotion.PromotionUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.promotion.PromotionResponse;
import com.group.restaurant.enums.PromotionStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.PromotionMapper;
import com.group.restaurant.models.Promotion;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.repositories.PromotionRepository;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.services.PromotionService;
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
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final RestaurantRepository restaurantRepository;
    private final PromotionMapper promotionMapper;

    @Override
    public PromotionResponse createPromotion(PromotionAddRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhà hàng với ID: " + request.getRestaurantId()));

        if (request.getCode() != null && promotionRepository.findByCode(request.getCode()).isPresent()) {
            throw new ConflictException("Mã khuyến mãi '" + request.getCode() + "' đã tồn tại");
        }

        Promotion promotion = promotionMapper.toEntity(request);
        promotion.setRestaurant(restaurant);
        promotion.setStatus(PromotionStatus.ACTIVE);
        if (promotion.getPriority() == null) {
            promotion.setPriority(0);
        }

        Promotion saved = promotionRepository.save(promotion);
        return promotionMapper.toResponse(saved);
    }

    @Override
    public PromotionResponse updatePromotion(Long id, PromotionUpdateRequest request) {
        Promotion promotion = findById(id);
        promotionMapper.updateEntity(promotion, request);
        Promotion updated = promotionRepository.save(promotion);
        return promotionMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getPromotionById(Long id) {
        Promotion promotion = findById(id);
        return promotionMapper.toResponse(promotion);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> getPromotionsByRestaurant(Long restaurantId, PromotionStatus status, Pageable pageable) {
        List<Promotion> promotions;

        if (status != null) {
            promotions = promotionRepository.findByRestaurantIdAndStatus(restaurantId, status);
        } else {
            promotions = promotionRepository.findByRestaurantId(restaurantId);
        }

        List<PromotionResponse> responses = promotions.stream()
                .map(promotionMapper::toResponse)
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
        promotion.setStatus(status);
        promotionRepository.save(promotion);
    }

    @Override
    public void deletePromotion(Long id) {
        Promotion promotion = findById(id);
        promotionRepository.delete(promotion);
    }

    private Promotion findById(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khuyến mãi với ID: " + id));
    }
}
