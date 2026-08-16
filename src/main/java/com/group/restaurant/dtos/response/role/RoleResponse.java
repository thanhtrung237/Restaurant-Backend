package com.group.restaurant.dtos.response.role;

import com.group.restaurant.enums.RoleScope;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
@Builder
public class RoleResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String name;
    private String code;
    private RoleScope scope;
    private Integer assignmentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
