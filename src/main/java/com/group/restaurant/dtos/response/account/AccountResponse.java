package com.group.restaurant.dtos.response.account;

import com.group.restaurant.dtos.response.assignment.AssignmentResponse;
import com.group.restaurant.enums.AccountStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AccountResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String fullName;
    private String employeeCode;
    private String username;
    private String email;
    private AccountStatus status;
    private List<AssignmentResponse> assignments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
