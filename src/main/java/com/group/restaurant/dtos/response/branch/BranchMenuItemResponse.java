package com.group.restaurant.dtos.response.branch;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.group.restaurant.enums.StockStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


@Getter
@Setter
@Builder
public class BranchMenuItemResponse {

    private Long dishId;
    private String dishName;
    private String description;
    private String imageUrl;
    private String kitchenStationType;
    private Boolean soldByWeight;
    private Long categoryId;
    private String categoryName;

    // Giá gốc cấp nhà hàng
    private BigDecimal basePrice;

    // Giá đè của chi nhánh (null = không đè, dùng giá gốc)
    private BigDecimal overridePrice;

    // Giá thực sự hiển thị (resolve theo BR-17)
    private BigDecimal effectivePrice;

    // Chênh lệch giữa giá gốc và giá đè (dùng cho màn hình admin FR-MNU-04)
    private BigDecimal priceDifference;

    // true = chi nhánh có bản đè giá
    private boolean hasOverride;

    // true = món bị ẩn tại chi nhánh này
    @Getter(onMethod_ = @JsonProperty("isHidden"))
    private boolean isHidden;

    // Trạng thái tồn kho tại chi nhánh (FR-MNU-03)
    private StockStatus stockStatus;

    // true = món riêng của chi nhánh (không phải món gốc nhà hàng)
    @Getter(onMethod_ = @JsonProperty("isBranchSpecific"))
    private boolean isBranchSpecific;
}
