package com.group.restaurant.mappers;

import com.group.restaurant.dtos.response.manualDiscount.ManualDiscountResponse;
import com.group.restaurant.models.ManualDiscount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ManualDiscountMapper {

    @Mapping(target = "approvedById", source = "approvedBy.id")
    @Mapping(target = "approvedByName", source = "approvedBy.fullName")
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "invoiceId", source = "invoice.id")
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "createdByName", ignore = true)
    ManualDiscountResponse toResponse(ManualDiscount manualDiscount);
}
