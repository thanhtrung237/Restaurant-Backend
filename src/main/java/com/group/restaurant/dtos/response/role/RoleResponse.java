package com.group.restaurant.dtos.response.role;

import com.group.restaurant.enums.RoleScope;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

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
