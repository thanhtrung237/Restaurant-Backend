package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.branch.BranchAddRequest;
import com.group.restaurant.dtos.request.branch.BranchUpdateRequest;
import com.group.restaurant.dtos.response.branch.BranchListResponse;
import com.group.restaurant.dtos.response.branch.BranchResponse;
import com.group.restaurant.models.Branch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = "spring", 
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface BranchMapper {

    @Mapping(target = "restaurant.id", source = "restaurantId")
    Branch toEntity(BranchAddRequest request);

    void updateEntity(@MappingTarget Branch branch, BranchUpdateRequest request);

    @Mapping(target = "restaurantId", source = "restaurant.id")
    @Mapping(target = "restaurantName", source = "restaurant.name")
    @Mapping(target = "updatedAt", source = "modifiedAt")
    @Mapping(target = "tableCount", ignore = true)
    @Mapping(target = "activeSessionCount", ignore = true)
    @Mapping(target = "hasOpenSessionWarning", ignore = true)
    @Mapping(target = "openSessionCount", ignore = true)
    BranchResponse toResponse(Branch branch);

    @Mapping(target = "tableCount", ignore = true)
    BranchListResponse toListResponse(Branch branch);
}
