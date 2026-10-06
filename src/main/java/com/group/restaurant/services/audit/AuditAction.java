package com.group.restaurant.services.audit;

/**
 * Loại hành động ghi vào nhật ký (FR-LOG-01) — dùng làm giá trị cho
 * {@code AuditLog.actionType} để FE lọc được theo nhóm.
 *
 * Cố ý để dạng hằng String chứ không phải enum có converter: cột `action_type` là
 * VARCHAR, và danh sách hành động sẽ còn mở rộng — thêm hằng mới không cần migration.
 */
public final class AuditAction {

    private AuditAction() {
    }

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String STATUS_CHANGE = "STATUS_CHANGE";

    /** Hủy hóa đơn / hủy dòng món / hủy phiên. */
    public static final String CANCEL = "CANCEL";
    /** Ghi nhận thanh toán. */
    public static final String PAYMENT = "PAYMENT";
    /** Chiết khấu thủ công (cần đối soát chặt nhất — BR ngưỡng duyệt). */
    public static final String DISCOUNT = "DISCOUNT";
    /** Phát hành hóa đơn điện tử. */
    public static final String ISSUE_EINVOICE = "ISSUE_EINVOICE";
    /** Thay đổi cấu hình nhà hàng / chi nhánh (VAT, phí DV, ngưỡng duyệt...). */
    public static final String CONFIG_CHANGE = "CONFIG_CHANGE";
    /** Cấp/thu hồi vai trò, đổi mật khẩu, khóa tài khoản. */
    public static final String PERMISSION_CHANGE = "PERMISSION_CHANGE";

    /** Đăng nhập — cả thành công lẫn thất bại (FR-ADM-04, FR-LOG-01). */
    public static final String LOGIN = "LOGIN";
    /** Đăng xuất (FR-ADM-04). */
    public static final String LOGOUT = "LOGOUT";
}
