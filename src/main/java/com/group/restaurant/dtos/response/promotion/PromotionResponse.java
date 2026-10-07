package com.group.restaurant.dtos.response.promotion;

import com.group.restaurant.enums.ApplyLevel;
import com.group.restaurant.enums.PromotionStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PromotionResponse {

    private Long id;
    private Long restaurantId;
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private String timeWindow;
    private String daysOfWeek;
    private ApplyLevel applyLevel;
    private String condition;
    private String action;
    private Integer priority;
    private PromotionStatus status;
    private String code;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
