package com.group.restaurant.dtos.response.auth;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LoginResponse {

    private String accessToken;
    private Long accountId;
    private Long restaurantId;
    private String username;
    private String fullName;
    private List<String> roles;
}
