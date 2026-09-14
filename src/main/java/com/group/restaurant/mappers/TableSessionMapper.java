package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.tableSession.TableSessionOpenRequest;
import com.group.restaurant.dtos.request.tableSession.TableSessionUpdateRequest;
import com.group.restaurant.dtos.response.tableSession.TableSessionResponse;
import com.group.restaurant.models.TableSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TableSessionMapper {

    @Mapping(target = "table.id", source = "tableId")
    TableSession toEntity(TableSessionOpenRequest request);

    void updateEntity(@MappingTarget TableSession tableSession, TableSessionUpdateRequest request);

    @Mapping(target = "tableId", source = "table.id")
    @Mapping(target = "tableName", source = "table.name")
    @Mapping(target = "orderCount", ignore = true)
    TableSessionResponse toResponse(TableSession tableSession);
}
