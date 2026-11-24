package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.dishOverride.DishOverrideAddRequest;
import com.group.restaurant.dtos.request.dishOverride.DishOverrideUpdateRequest;
import com.group.restaurant.dtos.response.dishOverride.DishOverrideResponse;
import com.group.restaurant.models.DishOverride;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DishOverrideMapper {

    @Mapping(target = "branch.id", source = "branchId")
    @Mapping(target = "dish.id", source = "dishId")
    DishOverride toEntity(DishOverrideAddRequest request);

    void updateEntity(@MappingTarget DishOverride dishOverride, DishOverrideUpdateRequest request);

    @Mapping(target = "branchId", source = "branch.id")
    @Mapping(target = "branchName", source = "branch.name")
    @Mapping(target = "dishId", source = "dish.id")
    @Mapping(target = "dishName", source = "dish.name")
    DishOverrideResponse toResponse(DishOverride dishOverride);
}
