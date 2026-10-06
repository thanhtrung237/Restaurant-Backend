package com.group.restaurant.dtos.response.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private Long accountId;
    private Long restaurantId;
    private String username;
    private String fullName;
    private List<String> roles;
}
