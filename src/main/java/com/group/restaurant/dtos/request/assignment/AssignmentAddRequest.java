package com.group.restaurant.dtos.request.assignment;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignmentAddRequest {

    @NotNull(message = "Account ID không được để trống")
    private Long accountId;

    @NotNull(message = "Role ID không được để trống")
    private Long roleId;

    private Long branchId;
}
