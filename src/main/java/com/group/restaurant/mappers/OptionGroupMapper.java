package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.optionGroup.OptionGroupAddRequest;
import com.group.restaurant.dtos.request.optionGroup.OptionGroupUpdateRequest;
import com.group.restaurant.dtos.response.optionGroup.OptionGroupResponse;
import com.group.restaurant.models.OptionGroup;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OptionGroupMapper {

    @Mapping(target = "restaurant.id", source = "restaurantId")
    OptionGroup toEntity(OptionGroupAddRequest request);

    void updateEntity(@MappingTarget OptionGroup optionGroup, OptionGroupUpdateRequest request);

    @Mapping(target = "restaurantId", source = "restaurant.id")
    @Mapping(target = "restaurantName", source = "restaurant.name")
    OptionGroupResponse toResponse(OptionGroup optionGroup);
}
