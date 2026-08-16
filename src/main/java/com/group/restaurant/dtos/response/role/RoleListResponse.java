package com.group.restaurant.dtos.response.role;

import com.group.restaurant.enums.RoleScope;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Builder
public class RoleListResponse {

    private Long id;
    private String name;
    private String code;
    private RoleScope scope;
    private Integer assignmentCount;
}
