package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.payment.PaymentAddRequest;
import com.group.restaurant.dtos.response.payment.PaymentResponse;
import com.group.restaurant.models.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PaymentMapper {

    @Mapping(target = "invoice.id", source = "invoiceId")
    Payment toEntity(PaymentAddRequest request);

    @Mapping(target = "invoiceId", source = "invoice.id")
    @Mapping(target = "changeAmount", ignore = true)
    PaymentResponse toResponse(Payment payment);
}
