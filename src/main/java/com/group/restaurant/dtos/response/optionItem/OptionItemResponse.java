package com.group.restaurant.dtos.response.optionItem;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class OptionItemResponse {

    private Long id;
    private Long optionGroupId;
    private String optionGroupName;
    private String name;
    private BigDecimal extraPrice;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
