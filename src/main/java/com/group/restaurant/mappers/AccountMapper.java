package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.account.AccountAddRequest;
import com.group.restaurant.dtos.request.account.AccountUpdateRequest;
import com.group.restaurant.dtos.response.account.AccountResponse;
import com.group.restaurant.models.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccountMapper {

    @Mapping(target = "restaurant.id", source = "restaurantId")
    @Mapping(target = "passwordHash", ignore = true)
    Account toEntity(AccountAddRequest request);

    void updateEntity(@MappingTarget Account account, AccountUpdateRequest request);

    @Mapping(target = "restaurantId", source = "restaurant.id")
    @Mapping(target = "restaurantName", source = "restaurant.name")
    @Mapping(target = "updatedAt", source = "modifiedAt")
    @Mapping(target = "assignments", ignore = true)
    AccountResponse toResponse(Account account);
}
