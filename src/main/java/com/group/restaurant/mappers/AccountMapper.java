package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.account.AccountAddRequest;
import com.group.restaurant.dtos.request.account.AccountUpdateRequest;
import com.group.restaurant.dtos.response.account.AccountResponse;
import com.group.restaurant.models.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * `nullValuePropertyMappingStrategy = IGNORE` là BẮT BUỘC ở đây.
 *
 * `AccountUpdateRequest` không có trường nào `@NotBlank`, nên client gửi kèm mỗi họ tên là
 * hợp lệ. Với chiến lược mặc định (SET_TO_NULL), MapStruct ghi `null` đè lên `employeeCode`
 * và `email` — `employee_code` là cột NOT NULL nên sinh DataIntegrityViolation, trả 400
 * "Lỗi dữ liệu liên kết hoặc trùng lặp dữ liệu trong hệ thống" mà không nói được là vì sao.
 * Sửa một trường mà mất trường khác còn tệ hơn: đó là mất dữ liệu im lặng.
 *
 * Với IGNORE: field không gửi ⇒ giữ nguyên; gửi chuỗi rỗng ⇒ xoá trắng.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface AccountMapper {

    // QĐ-03: mã nhân viên do service sinh, không đến từ request.
    @Mapping(target = "restaurant.id", source = "restaurantId")
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "employeeCode", ignore = true)
    Account toEntity(AccountAddRequest request);

    /** Mã nhân viên là mã cố định — KHÔNG cho sửa (QĐ-03). DTO cũng đã bỏ trường này. */
    @Mapping(target = "employeeCode", ignore = true)
    void updateEntity(@MappingTarget Account account, AccountUpdateRequest request);

    @Mapping(target = "restaurantId", source = "restaurant.id")
    @Mapping(target = "restaurantName", source = "restaurant.name")
    @Mapping(target = "updatedAt", source = "modifiedAt")
    @Mapping(target = "assignments", ignore = true)
    AccountResponse toResponse(Account account);
}
