package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.diningTable.DiningTableAddRequest;
import com.group.restaurant.dtos.request.diningTable.DiningTableUpdateRequest;
import com.group.restaurant.dtos.response.diningTable.DiningTableResponse;
import com.group.restaurant.models.DiningTable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DiningTableMapper {

    @Mapping(target = "branch.id", source = "branchId")
    @Mapping(target = "area.id", source = "areaId")
    DiningTable toEntity(DiningTableAddRequest request);

    void updateEntity(@MappingTarget DiningTable diningTable, DiningTableUpdateRequest request);

    @Mapping(target = "branchId", source = "branch.id")
    @Mapping(target = "branchName", source = "branch.name")
    @Mapping(target = "areaId", source = "area.id")
    @Mapping(target = "areaName", source = "area.name")
    @Mapping(target = "hasOpenSession", ignore = true)
    DiningTableResponse toResponse(DiningTable diningTable);
}
