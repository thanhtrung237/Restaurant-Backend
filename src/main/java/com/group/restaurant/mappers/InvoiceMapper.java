package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.invoice.InvoiceCreateRequest;
import com.group.restaurant.dtos.response.invoice.InvoiceListResponse;
import com.group.restaurant.dtos.response.invoice.InvoiceResponse;
import com.group.restaurant.models.Invoice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = {PaymentMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface InvoiceMapper {

    @Mapping(target = "branch.id", source = "branchId")
    @Mapping(target = "tableSession.id", source = "tableSessionId")
    @Mapping(target = "promotion.id", source = "promotionId")
    Invoice toEntity(InvoiceCreateRequest request);

    @Mapping(target = "branchId", source = "branch.id")
    @Mapping(target = "branchName", source = "branch.name")
    @Mapping(target = "tableSessionId", source = "tableSession.id")
    @Mapping(target = "tableName", source = "tableSession.table.name")
    @Mapping(target = "promotionId", source = "promotion.id")
    @Mapping(target = "promotionName", source = "promotion.name")
    @Mapping(target = "payments", source = "payments")
    @Mapping(target = "totalPaid", ignore = true)
    @Mapping(target = "remainingAmount", ignore = true)
    InvoiceResponse toResponse(Invoice invoice);

    @Mapping(target = "branchName", source = "branch.name")
    @Mapping(target = "tableName", source = "tableSession.table.name")
    InvoiceListResponse toListResponse(Invoice invoice);
}
