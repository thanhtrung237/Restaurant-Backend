package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.optionItem.OptionItemAddRequest;
import com.group.restaurant.dtos.request.optionItem.OptionItemUpdateRequest;
import com.group.restaurant.dtos.response.optionItem.OptionItemResponse;
import com.group.restaurant.models.OptionItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OptionItemMapper {

    @Mapping(target = "optionGroup.id", source = "optionGroupId")
    OptionItem toEntity(OptionItemAddRequest request);

    void updateEntity(@MappingTarget OptionItem optionItem, OptionItemUpdateRequest request);

    @Mapping(target = "optionGroupId", source = "optionGroup.id")
    @Mapping(target = "optionGroupName", source = "optionGroup.name")
    OptionItemResponse toResponse(OptionItem optionItem);
}
