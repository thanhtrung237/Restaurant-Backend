package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.order.OrderAddRequest;
import com.group.restaurant.dtos.request.order.OrderUpdateRequest;
import com.group.restaurant.dtos.response.order.OrderResponse;
import com.group.restaurant.models.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        uses = {OrderDetailMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderMapper {

    @Mapping(target = "tableSession.id", source = "tableSessionId")
    @Mapping(target = "orderedBy.id", source = "orderedById")
    Order toEntity(OrderAddRequest request);

    void updateEntity(@MappingTarget Order order, OrderUpdateRequest request);

    @Mapping(target = "tableSessionId", source = "tableSession.id")
    @Mapping(target = "orderedById", source = "orderedBy.id")
    @Mapping(target = "orderedByName", source = "orderedBy.fullName")
    @Mapping(target = "items", source = "orderDetails")
    OrderResponse toResponse(Order order);
}
