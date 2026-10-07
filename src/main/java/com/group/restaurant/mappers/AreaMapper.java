package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.area.AreaAddRequest;
import com.group.restaurant.dtos.request.area.AreaUpdateRequest;
import com.group.restaurant.dtos.response.area.AreaResponse;
import com.group.restaurant.models.Area;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface AreaMapper {

    @Mapping(target = "branch.id", source = "branchId")
    Area toEntity(AreaAddRequest request);

    void updateEntity(@MappingTarget Area area, AreaUpdateRequest request);

    @Mapping(target = "branchId", source = "branch.id")
    @Mapping(target = "branchName", source = "branch.name")
    @Mapping(target = "tableCount", ignore = true)
    AreaResponse toResponse(Area area);
}
