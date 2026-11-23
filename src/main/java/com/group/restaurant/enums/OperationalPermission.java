package com.group.restaurant.enums;

import java.util.Set;

public enum OperationalPermission {
    RESERVE_TABLE("Bàn & đặt bàn", "Đặt và hủy đặt bàn", Set.of("WAITER", "CASHIER", "BRANCH_MANAGER"), Set.of("WAITER", "BRANCH_MANAGER")),
    CONFIRM_ORDER("Gọi món & chế biến", "Xác nhận món vào bếp", Set.of("WAITER", "BRANCH_MANAGER")),
    WEIGH_ORDER("Gọi món & chế biến", "Nhập số cân", Set.of("WAITER", "BRANCH_MANAGER")),
    SERVE_ORDER("Gọi món & chế biến", "Xác nhận đã phục vụ hoặc đóng gói", Set.of("WAITER", "BRANCH_MANAGER")),
    MANAGE_STOCK("Thực đơn", "Báo hết hoặc bán lại món", Set.of("BRANCH_MANAGER")),
    VIEW_REPORT("Báo cáo", "Xem báo cáo chi nhánh", Set.of("BRANCH_MANAGER")),
    EXPORT_REPORT("Báo cáo", "Xuất báo cáo chi nhánh", Set.of("BRANCH_MANAGER"));

    public final String group;
    public final String label;
    public final Set<String> roles;
    public final Set<String> defaults;

    OperationalPermission(String group, String label, Set<String> roles) {
        this(group, label, roles, roles);
    }

    OperationalPermission(String group, String label, Set<String> roles, Set<String> defaults) {
        this.group = group;
        this.label = label;
        this.roles = roles;
        this.defaults = defaults;
    }
}
