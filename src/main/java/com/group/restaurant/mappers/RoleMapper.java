package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.role.RoleAddRequest;
import com.group.restaurant.dtos.request.role.RoleUpdateRequest;
import com.group.restaurant.dtos.response.role.RoleListResponse;
import com.group.restaurant.dtos.response.role.RoleResponse;
import com.group.restaurant.models.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = org.mapstruct.NullValuePropertyMappingStrategy.IGNORE)
public interface RoleMapper {

    @Mapping(target = "restaurant.id", source = "restaurantId")
    Role toEntity(RoleAddRequest request);

    void updateEntity(@MappingTarget Role role, RoleUpdateRequest request);

    @Mapping(target = "restaurantId", source = "restaurant.id")
    @Mapping(target = "restaurantName", source = "restaurant.name")
    @Mapping(target = "updatedAt", source = "modifiedAt")
    @Mapping(target = "assignmentCount", ignore = true)
    RoleResponse toResponse(Role role);

    @Mapping(target = "assignmentCount", ignore = true)
    RoleListResponse toListResponse(Role role);
}
