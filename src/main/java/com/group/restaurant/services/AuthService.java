package com.group.restaurant.services;

import com.group.restaurant.dtos.request.auth.LoginRequest;
import com.group.restaurant.dtos.response.auth.LoginResponse;
import com.group.restaurant.dtos.response.auth.RefreshTokenResponse;

public interface AuthService {

    record LoginResult(LoginResponse response, String refreshToken) {}

    LoginResult login(LoginRequest request);

    RefreshTokenResponse refreshToken(String refreshToken);

    void logout(String refreshToken);
}
