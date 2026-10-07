package com.group.restaurant.dtos.response.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 *  Món bán chạy / bán chậm
 */
@Getter
@Builder
public class TopDishResponse {

    // Bảng xếp hạng theo số lượng bán
    private List<DishRankItem> topByQuantity;

    // Bảng xếp hạng theo doanh thu
    private List<DishRankItem> topByRevenue;

    // Danh sách món bán chậm nhất (để cân nhắc loại khỏi menu)
    private List<DishRankItem> slowestDishes;

    @Getter
    @Builder
    public static class DishRankItem {
        private Long dishId;
        private String dishName;
        private String categoryName;
        private Long totalQuantity;       // Tổng số lượng bán
        private BigDecimal totalRevenue;  // Tổng doanh thu
        private int rank;                 // Thứ hạng
    }
}
