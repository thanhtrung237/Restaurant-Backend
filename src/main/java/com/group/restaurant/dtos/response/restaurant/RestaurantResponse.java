package com.group.restaurant.dtos.response.restaurant;

import com.group.restaurant.enums.RestaurantStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RestaurantResponse {

    private Long id;
    private String code;
    private String name;
    private String taxCode;
    private String address;
    private String phone;
    private String logoUrl;
    private RestaurantStatus status;
    private Integer branchCount;
    private Integer accountCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
