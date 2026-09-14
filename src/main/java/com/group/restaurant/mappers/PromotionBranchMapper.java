package com.group.restaurant.mappers;

import com.group.restaurant.dtos.response.promotionBranch.PromotionBranchResponse;
import com.group.restaurant.models.PromotionBranch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PromotionBranchMapper {

    @Mapping(target = "branchId", source = "branch.id")
    @Mapping(target = "branchName", source = "branch.name")
    @Mapping(target = "promotionId", source = "promotion.id")
    @Mapping(target = "promotionName", source = "promotion.name")
    PromotionBranchResponse toResponse(PromotionBranch promotionBranch);
}
