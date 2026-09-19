package com.group.restaurant.services.impl;

import com.group.restaurant.configurations.jwt.JwtUtil;
import com.group.restaurant.dtos.request.auth.LoginRequest;
import com.group.restaurant.dtos.response.auth.LoginResponse;
import com.group.restaurant.dtos.response.auth.RefreshTokenResponse;
import com.group.restaurant.enums.AccountStatus;
import com.group.restaurant.enums.TokenType;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.RefreshToken;
import com.group.restaurant.repositories.AccountRepository;
import com.group.restaurant.repositories.RefreshTokenRepository;
import com.group.restaurant.services.AuthService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest request) {
        Account account = accountRepository
                .findByRestaurantCodeAndUsername(request.getRestaurantCode(), request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Sai tên đăng nhập hoặc mật khẩu"));

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new BadCredentialsException("Sai tên đăng nhập hoặc mật khẩu");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new DisabledException("Tài khoản đã bị vô hiệu hóa");
        }

        String accessToken = jwtUtil.generateAccessToken(account);
        String refreshTokenStr = jwtUtil.generateRefreshToken(account);
        LocalDate expiryDate = jwtUtil.getExpirationDate(refreshTokenStr, TokenType.REFRESH_TOKEN);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenStr)
                .expiryDate(expiryDate)
                .deviceInfo(request.getDeviceInfo())
                .revoked(false)
                .account(account)
                .build();
        refreshTokenRepository.save(refreshToken);

        List<String> roles = List.of();
        if (account.getAssignments() != null && !account.getAssignments().isEmpty()) {
            roles = account.getAssignments().stream()
                    .map(a -> a.getRole().getCode())
                    .collect(Collectors.toList());
        }

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .accountId(account.getId())
                .restaurantId(account.getRestaurant().getId())
                .username(account.getUsername())
                .fullName(account.getFullName())
                .roles(roles)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshTokenResponse refreshToken(String refreshTokenStr) {
        if (refreshTokenStr == null || refreshTokenStr.isBlank()) {
            throw new InvalidParamException("Refresh token không được để trống");
        }

        jwtUtil.validateToken(refreshTokenStr, TokenType.REFRESH_TOKEN);

        String username = jwtUtil.getUsernameFromToken(refreshTokenStr, TokenType.REFRESH_TOKEN);

        RefreshToken dbToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token không tồn tại"));

        if (Boolean.TRUE.equals(dbToken.getRevoked())) {
            throw new JwtException("Refresh token đã bị thu hồi");
        }

        if (dbToken.getExpiryDate().isBefore(LocalDate.now())) {
            throw new JwtException("Refresh token đã hết hạn");
        }

        Account account = dbToken.getAccount();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new DisabledException("Tài khoản đã bị vô hiệu hóa");
        }

        String newAccessToken = jwtUtil.generateAccessToken(account);

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshTokenStr)
                .build();
    }

    @Override
    public void logout(HttpServletRequest request) {
        String refreshTokenStr = getRefreshTokenFromRequest(request);
        if (refreshTokenStr == null) return;

        refreshTokenRepository.findByToken(refreshTokenStr).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private String getRefreshTokenFromRequest(HttpServletRequest request) {
        // Hỗ trợ cả header và cookie
        String header = request.getHeader("Refresh-Token");
        if (header != null && !header.isBlank()) return header;

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) return cookie.getValue();
            }
        }
        return null;
    }
}
