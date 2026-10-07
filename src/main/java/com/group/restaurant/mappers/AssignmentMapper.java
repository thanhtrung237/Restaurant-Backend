package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.assignment.AssignmentAddRequest;
import com.group.restaurant.models.Assignment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AssignmentMapper {

    @Mapping(target = "account.id", source = "accountId")
    @Mapping(target = "role.id", source = "roleId")
    @Mapping(target = "branch.id", source = "branchId")
    Assignment toEntity(AssignmentAddRequest request);
}
