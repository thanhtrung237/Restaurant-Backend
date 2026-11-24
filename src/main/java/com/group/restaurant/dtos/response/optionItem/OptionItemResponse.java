package com.group.restaurant.dtos.response.optionItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

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
