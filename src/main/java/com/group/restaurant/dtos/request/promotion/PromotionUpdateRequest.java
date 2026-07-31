package com.group.restaurant.dtos.request.promotion;

import com.group.restaurant.enums.ApplyLevel;
import com.group.restaurant.enums.PromotionStatus;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PromotionUpdateRequest {

    @Size(max = 100, message = "Tên khuyến mãi không được vượt quá 100 ký tự")
    private String name;

    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 100, message = "Time window không được vượt quá 100 ký tự")
    private String timeWindow;

    @Size(max = 50, message = "Days of week không được vượt quá 50 ký tự")
    private String daysOfWeek;

    private ApplyLevel applyLevel;

    private String condition;

    private String action;

    private Integer priority;

    private PromotionStatus status;

    @Size(max = 50, message = "Code không được vượt quá 50 ký tự")
    private String code;
}
