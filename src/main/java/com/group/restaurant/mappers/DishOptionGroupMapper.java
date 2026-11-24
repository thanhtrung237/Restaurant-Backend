package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.dishOptionGroup.DishOptionGroupAddRequest;
import com.group.restaurant.dtos.response.dishOptionGroup.DishOptionGroupResponse;
import com.group.restaurant.models.DishOptionGroup;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DishOptionGroupMapper {

    @Mapping(target = "dish.id", source = "dishId")
    @Mapping(target = "optionGroup.id", source = "optionGroupId")
    DishOptionGroup toEntity(DishOptionGroupAddRequest request);

    @Mapping(target = "dishId", source = "dish.id")
    @Mapping(target = "dishName", source = "dish.name")
    @Mapping(target = "optionGroupId", source = "optionGroup.id")
    @Mapping(target = "optionGroupName", source = "optionGroup.name")
    @Mapping(target = "selectionType", source = "optionGroup.selectionType")
    @Mapping(target = "minSelect", source = "optionGroup.minSelect")
    @Mapping(target = "maxSelect", source = "optionGroup.maxSelect")
    DishOptionGroupResponse toResponse(DishOptionGroup dishOptionGroup);
}
