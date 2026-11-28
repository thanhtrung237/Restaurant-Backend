package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.request.auth.LoginRequest;
import com.group.restaurant.dtos.response.auth.LoginResponse;
import com.group.restaurant.dtos.response.auth.RefreshTokenResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.services.AuthService;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
// Require a custom request header for cookie-based auth so cross-origin forms cannot submit it.
@RequestMapping(path = "${api.prefix}/auth", headers = "X-Auth-Request=1")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refreshToken";

    private final AuthService authService;

    @Value("${api.prefix}")
    private String apiPrefix;

    @Value("${spring.app.jwtRefreshExpirationDays}")
    private long refreshExpirationDays;

    @Value("${app.auth.cookie.secure}")
    private boolean secureCookie;

    @PostMapping("/login")
    public ResponseEntity<ResponseSuccess<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        var result = authService.login(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie(result.refreshToken(), Duration.ofDays(refreshExpirationDays)))
                .body(new ResponseSuccess<>(OK, "Đăng nhập thành công", result.response()));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ResponseSuccess<RefreshTokenResponse>> refreshToken(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        new ResponseSuccess<>(
                                OK,
                                "Làm mới token thành công",
                                authService.refreshToken(refreshToken)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ResponseSuccess<Void>> logout(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO))
                .body(new ResponseSuccess<>(OK, "Đăng xuất thành công", null));
    }

    private String refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path(apiPrefix + "/auth")
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
