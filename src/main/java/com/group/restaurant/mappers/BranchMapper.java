package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.branch.BranchAddRequest;
import com.group.restaurant.dtos.request.branch.BranchUpdateRequest;
import com.group.restaurant.dtos.response.branch.BranchListResponse;
import com.group.restaurant.dtos.response.branch.BranchResponse;
import com.group.restaurant.models.Branch;
import java.time.format.DateTimeFormatter;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class BranchMapper {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Mapping(target = "restaurant.id", source = "restaurantId")
    public abstract Branch toEntity(BranchAddRequest request);

    public abstract void updateEntity(@MappingTarget Branch branch, BranchUpdateRequest request);

    @Mapping(target = "restaurantId", source = "restaurant.id")
    @Mapping(target = "restaurantName", source = "restaurant.name")
    @Mapping(target = "updatedAt", source = "modifiedAt")
    @Mapping(target = "tableCount", ignore = true)
    @Mapping(target = "activeSessionCount", ignore = true)
    @Mapping(target = "hasOpenSessionWarning", ignore = true)
    @Mapping(target = "openSessionCount", ignore = true)
    public abstract BranchResponse toResponse(Branch branch);

    @Mapping(target = "tableCount", ignore = true)
    @Mapping(target = "managerName", ignore = true)
    @Mapping(target = "openingHours", ignore = true)
    public abstract BranchListResponse toListResponse(Branch branch);

    @AfterMapping
    protected void setOpeningHours(@MappingTarget BranchListResponse response, Branch branch) {
        if (branch.getOpeningTime() != null && branch.getClosingTime() != null) {
            String opening = branch.getOpeningTime().format(TIME_FORMATTER);
            String closing = branch.getClosingTime().format(TIME_FORMATTER);
            response.setOpeningHours(opening + " - " + closing);
        }
    }
}
