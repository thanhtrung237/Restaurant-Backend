package com.group.restaurant.dtos.request.promotion;

import com.group.restaurant.enums.ApplyLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PromotionAddRequest {

    @NotNull(message = "Restaurant ID không được để trống")
    private Long restaurantId;

    @NotBlank(message = "Tên khuyến mãi không được để trống")
    @Size(max = 100, message = "Tên khuyến mãi không được vượt quá 100 ký tự")
    private String name;

    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
    private String description;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private LocalDate endDate;

    @Size(max = 100, message = "Time window không được vượt quá 100 ký tự")
    private String timeWindow;

    @Size(max = 50, message = "Days of week không được vượt quá 50 ký tự")
    private String daysOfWeek;

    @NotNull(message = "Apply level không được để trống")
    private ApplyLevel applyLevel;

    private String condition;

    private String action;

    private Integer priority;

    @Size(max = 50, message = "Code không được vượt quá 50 ký tự")
    private String code;
}
