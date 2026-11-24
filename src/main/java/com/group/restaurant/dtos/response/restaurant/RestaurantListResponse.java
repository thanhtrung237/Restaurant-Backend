package com.group.restaurant.dtos.response.restaurant;

import com.group.restaurant.enums.RestaurantStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RestaurantListResponse {

    private Long id;
    private String code;
    private String name;
    private String taxCode;
    private String address;
    private String phone;
    private RestaurantStatus status;
    private Integer branchCount;
}
