package com.group.restaurant.dtos.response.assignment;

import com.group.restaurant.enums.RoleScope;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AssignmentResponse {

    private Long id;

    private Long accountId;
    private String accountName;
    private String employeeCode;

    private Long roleId;
    private String roleName;
    private String roleCode;
    private RoleScope roleScope;

    private Long branchId;
    private String branchName;
    private String branchCode;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
