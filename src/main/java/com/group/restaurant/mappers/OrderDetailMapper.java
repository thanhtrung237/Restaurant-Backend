package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.orderDetail.OrderDetailAddRequest;
import com.group.restaurant.dtos.request.orderDetail.OrderDetailUpdateRequest;
import com.group.restaurant.dtos.response.orderDetail.OrderDetailResponse;
import com.group.restaurant.models.OrderDetail;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderDetailMapper {

    @Mapping(target = "dish.id", source = "dishId")
    @Mapping(target = "promotion.id", source = "promotionId")
    OrderDetail toEntity(OrderDetailAddRequest request);

    void updateEntity(@MappingTarget OrderDetail orderDetail, OrderDetailUpdateRequest request);

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "dishId", source = "dish.id")
    @Mapping(target = "dishName", source = "dish.name")
    @Mapping(target = "promotionId", source = "promotion.id")
    @Mapping(target = "promotionName", source = "promotion.name")
    @Mapping(target = "isBillable", source = "isBillable")
    @Mapping(target = "options", ignore = true) // Sẽ được set thủ công trong service
    OrderDetailResponse toResponse(OrderDetail orderDetail);
}
