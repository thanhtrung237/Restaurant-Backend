package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.promotion.PromotionAddRequest;
import com.group.restaurant.dtos.request.promotion.PromotionUpdateRequest;
import com.group.restaurant.dtos.response.promotion.PromotionResponse;
import com.group.restaurant.models.Promotion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PromotionMapper {

    @Mapping(target = "restaurant.id", source = "restaurantId")
    Promotion toEntity(PromotionAddRequest request);

    void updateEntity(@MappingTarget Promotion promotion, PromotionUpdateRequest request);

    @Mapping(target = "restaurantId", source = "restaurant.id")
    PromotionResponse toResponse(Promotion promotion);
}
