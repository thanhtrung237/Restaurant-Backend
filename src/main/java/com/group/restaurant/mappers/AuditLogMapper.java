package com.group.restaurant.mappers;

import com.group.restaurant.dtos.response.auditLog.AuditLogResponse;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.AuditLog;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Restaurant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Map nhật ký sang response.
 *
 * Bốn quan hệ (restaurant, branch, performedBy, approvedBy) đều trỏ tới thực thể có
 * {@code @SQLRestriction("is_deleted = false")}. Bản ghi đích bị XOÁ MỀM sau khi dòng nhật ký
 * đã sinh ra thì proxy không giải được, và lần đầu chạm tới tên là
 * {@code EntityNotFoundException} — nổ cả API đọc nhật ký, không chỉ dòng đó
 * (be-issues mục 19; thực tế gặp: {@code Unable to find Branch with id 4}).
 *
 * Nhật ký là sổ ghi việc ĐÃ xảy ra: mất tên một thực thể đã xoá không được phép làm mất cả sổ.
 * Nên mỗi trường đi qua {@link SoftDeleteSafe}, trả {@code null} cho đúng trường đó.
 *
 * Hướng lâu dài (chưa làm vì đổi schema): lưu thẳng tên dạng GIÁ TRỊ trên chính dòng nhật ký
 * lúc ghi, thay vì khoá ngoại — xem be-issues mục 19.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {SoftDeleteSafe.class, Restaurant.class, Branch.class, Account.class})
public interface AuditLogMapper {

    @Mapping(target = "restaurantId", expression = "java(SoftDeleteSafe.get(auditLog.getRestaurant(), Restaurant::getId))")
    @Mapping(target = "restaurantName", expression = "java(SoftDeleteSafe.get(auditLog.getRestaurant(), Restaurant::getName))")
    @Mapping(target = "branchId", expression = "java(SoftDeleteSafe.get(auditLog.getBranch(), Branch::getId))")
    @Mapping(target = "branchName", expression = "java(SoftDeleteSafe.get(auditLog.getBranch(), Branch::getName))")
    @Mapping(target = "performedById", expression = "java(SoftDeleteSafe.get(auditLog.getPerformedBy(), Account::getId))")
    @Mapping(target = "performedByName", expression = "java(SoftDeleteSafe.get(auditLog.getPerformedBy(), Account::getFullName))")
    @Mapping(target = "approvedById", expression = "java(SoftDeleteSafe.get(auditLog.getApprovedBy(), Account::getId))")
    @Mapping(target = "approvedByName", expression = "java(SoftDeleteSafe.get(auditLog.getApprovedBy(), Account::getFullName))")
    AuditLogResponse toResponse(AuditLog auditLog);
}
