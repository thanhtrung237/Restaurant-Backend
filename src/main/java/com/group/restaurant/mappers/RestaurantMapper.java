package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.restaurant.RestaurantAddRequest;
import com.group.restaurant.dtos.request.restaurant.RestaurantUpdateRequest;
import com.group.restaurant.dtos.response.restaurant.RestaurantListResponse;
import com.group.restaurant.dtos.response.restaurant.RestaurantResponse;
import com.group.restaurant.models.Restaurant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = org.mapstruct.NullValuePropertyMappingStrategy.IGNORE)
public interface RestaurantMapper {

    Restaurant toEntity(RestaurantAddRequest request);

    void updateEntity(@MappingTarget Restaurant restaurant, RestaurantUpdateRequest request);

    @Mapping(target = "updatedAt", source = "modifiedAt")
    @Mapping(target = "branchCount", ignore = true)
    @Mapping(target = "accountCount", ignore = true)
    RestaurantResponse toResponse(Restaurant restaurant);

    @Mapping(target = "branchCount", ignore = true)
    RestaurantListResponse toListResponse(Restaurant restaurant);
}
