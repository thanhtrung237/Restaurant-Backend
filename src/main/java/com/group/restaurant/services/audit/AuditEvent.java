package com.group.restaurant.services.audit;

import lombok.Builder;
import lombok.Getter;

/**
 * Một hành động cần ghi nhật ký. Dùng builder thay vì hàm 8 tham số để chỗ gọi
 * đọc được ngay đang ghi cái gì.
 *
 * `performedBy` KHÔNG nằm ở đây — {@code AuditLogService} tự lấy từ SecurityContext,
 * để chỗ gọi không thể ghi sai người thực hiện.
 */
@Getter
@Builder
public class AuditEvent {

    /** Tên entity bị tác động: "Invoice", "Payment", "ManualDiscount", "Branch"... */
    private final String entityType;

    /** ID bản ghi bị tác động. */
    private final Long entityId;

    /** Một trong các hằng của {@link AuditAction}. */
    private final String actionType;

    /** Chi nhánh liên quan (null với hành động ở cấp nhà hàng). */
    private final Long branchId;

    /**
     * Nhà hàng liên quan. Để null thì {@code AuditLogService} suy ra từ tài khoản
     * đang đăng nhập; chỉ truyền tay khi hành động do khách (không đăng nhập) gây ra.
     */
    private final Long restaurantId;

    private final String valueBefore;
    private final String valueAfter;
    private final String reason;

    /** Người duyệt (ví dụ QL chi nhánh duyệt chiết khấu vượt ngưỡng). */
    private final Long approvedByAccountId;

    /**
     * Người thực hiện, khi KHÔNG lấy được từ SecurityContext.
     * Cần cho sự kiện ĐĂNG NHẬP: lúc ghi log thì chưa có Authentication nào.
     */
    private final Long performedByAccountId;
}
