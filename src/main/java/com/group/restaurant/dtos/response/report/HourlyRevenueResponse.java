package com.group.restaurant.dtos.response.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Doanh thu theo khung giờ
 */
@Getter
@Builder
public class HourlyRevenueResponse {

    // Phân bổ theo từng giờ trong ngày (0-23)
    private List<HourSlot> hourlySlots;

    // So sánh theo ngày trong tuần
    private List<DayOfWeekSlot> dayOfWeekSlots;

    @Getter
    @Builder
    public static class HourSlot {
        private int hour;                // 0-23
        private String label;            // "08:00-09:00"
        private BigDecimal revenue;
        private Long tableCount;         // số bàn phục vụ trong giờ đó
    }

    @Getter
    @Builder
    public static class DayOfWeekSlot {
        private int dayOfWeek;           // 1=Thứ 2 ... 7=Chủ nhật
        private String label;            // "Thứ 2", "Thứ 3"...
        private BigDecimal revenue;
        private Long invoiceCount;
    }
}
