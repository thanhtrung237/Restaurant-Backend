package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.request.auth.LoginRequest;
import com.group.restaurant.dtos.response.auth.LoginResponse;
import com.group.restaurant.dtos.response.auth.RefreshTokenResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ResponseSuccess<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(OK, "Đăng nhập thành công", authService.login(request)));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ResponseSuccess<RefreshTokenResponse>> refreshToken(
            @RequestHeader(value = "Refresh-Token", required = false) String refreshToken,
            HttpServletRequest request) {
        // Lấy từ header hoặc để service tự lấy từ cookie
        String token = refreshToken != null ? refreshToken : extractFromCookie(request);
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK, "Làm mới token thành công", authService.refreshToken(token)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ResponseSuccess<Void>> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Đăng xuất thành công", null));
    }

    private String extractFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if ("refreshToken".equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
