package com.group.restaurant.services.impl;

import com.group.restaurant.configurations.jwt.JwtUtil;
import com.group.restaurant.dtos.request.auth.LoginRequest;
import com.group.restaurant.dtos.response.auth.LoginResponse;
import com.group.restaurant.dtos.response.auth.RefreshTokenResponse;
import com.group.restaurant.enums.AccountStatus;
import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.enums.TokenType;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.Assignment;
import com.group.restaurant.models.RefreshToken;
import com.group.restaurant.models.Role;
import com.group.restaurant.repositories.AccountRepository;
import com.group.restaurant.repositories.AssignmentRepository;
import com.group.restaurant.repositories.RefreshTokenRepository;
import com.group.restaurant.repositories.RoleRepository;
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.AuthService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import io.jsonwebtoken.JwtException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RoleRepository roleRepository;
    private final AssignmentRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditLogService auditLogService;

    @Override
    public LoginResult login(LoginRequest request) {
        Account account;

        // Platform Admin không cần restaurantCode (để null hoặc empty)
        if (request.getRestaurantCode() == null || request.getRestaurantCode().isBlank()) {
            account =
                    accountRepository
                            .findPlatformAdminByUsername(request.getUsername())
                            .orElse(null);
        } else {
            account =
                    accountRepository
                            .findByRestaurantCodeAndUsername(
                                    request.getRestaurantCode(), request.getUsername())
                            .orElse(null);
        }

        // FR-ADM-04: "Ghi nhật ký: thời điểm, thành công hay thất bại."
        // record() chạy REQUIRES_NEW nên dòng log vẫn commit dù sau đó ném exception.
        if (account == null) {
            recordLoginAttempt(request, null, false, "Không tìm thấy tài khoản");
            throw new BadCredentialsException("Sai tên đăng nhập hoặc mật khẩu");
        }

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            recordLoginAttempt(request, account, false, "Sai mật khẩu");
            throw new BadCredentialsException("Sai tên đăng nhập hoặc mật khẩu");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            recordLoginAttempt(request, account, false, "Tài khoản đã bị vô hiệu hóa");
            throw new DisabledException("Tài khoản đã bị vô hiệu hóa");
        }

        // Đảm bảo Platform Admin luôn có phân công vai trò PLATFORM_ADMIN
        List<Assignment> currentAssignments = assignmentRepository.findByAccountId(account.getId());
        if (account.getRestaurant() == null && currentAssignments.isEmpty()) {
            Role platformRole =
                    roleRepository
                            .findByCode("PLATFORM_ADMIN")
                            .orElseGet(
                                    () -> {
                                        Role newRole =
                                                Role.builder()
                                                        .code("PLATFORM_ADMIN")
                                                        .name("Platform Admin")
                                                        .scope(RoleScope.PLATFORM)
                                                        .restaurant(null)
                                                        .build();
                                        return roleRepository.save(newRole);
                                    });

            Assignment assignment =
                    Assignment.builder().account(account).role(platformRole).branch(null).build();
            assignment = assignmentRepository.save(assignment);
            currentAssignments = List.of(assignment);
            if (account.getAssignments() != null) {
                try {
                    account.getAssignments().add(assignment);
                } catch (UnsupportedOperationException ignored) {
                }
            }
        }

        if (account.getRestaurant() == null
                && currentAssignments.stream()
                        .noneMatch(
                                assignment ->
                                        assignment.getRole() != null
                                                && "PLATFORM_ADMIN"
                                                        .equals(assignment.getRole().getCode()))) {
            recordLoginAttempt(
                    request, account, false, "Tài khoản chưa có quyền quản trị nền tảng");
            throw new BadCredentialsException("Sai tên đăng nhập hoặc mật khẩu");
        }

        String accessToken = jwtUtil.generateAccessToken(account);
        String refreshTokenStr = jwtUtil.generateRefreshToken(account);
        LocalDate expiryDate = jwtUtil.getExpirationDate(refreshTokenStr, TokenType.REFRESH_TOKEN);

        RefreshToken refreshToken =
                RefreshToken.builder()
                        .token(refreshTokenStr)
                        .expiryDate(expiryDate)
                        .deviceInfo(request.getDeviceInfo())
                        .revoked(false)
                        .account(account)
                        .build();
        refreshTokenRepository.save(refreshToken);

        List<String> roles =
                currentAssignments.stream()
                        .filter(a -> a.getRole() != null && a.getRole().getCode() != null)
                        .map(a -> a.getRole().getCode())
                        .collect(Collectors.toList());

        recordLoginAttempt(request, account, true, null);

        LoginResponse response =
                LoginResponse.builder()
                        .accessToken(accessToken)
                        .accountId(account.getId())
                        .restaurantId(
                                account.getRestaurant() != null
                                        ? account.getRestaurant().getId()
                                        : null)
                        .username(account.getUsername())
                        .fullName(account.getFullName())
                        .roles(roles)
                        .build();
        return new LoginResult(response, refreshTokenStr);
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshTokenResponse refreshToken(String refreshTokenStr) {
        if (refreshTokenStr == null || refreshTokenStr.isBlank()) {
            throw new BadCredentialsException("Phiên đăng nhập đã hết hạn");
        }

        try {
            jwtUtil.validateToken(refreshTokenStr, TokenType.REFRESH_TOKEN);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Phiên đăng nhập đã hết hạn", ex);
        }

        RefreshToken dbToken =
                refreshTokenRepository
                        .findByToken(refreshTokenStr)
                        .orElseThrow(
                                () -> new BadCredentialsException("Phiên đăng nhập đã hết hạn"));

        if (Boolean.TRUE.equals(dbToken.getRevoked())
                || dbToken.getExpiryDate().isBefore(LocalDate.now())) {
            throw new BadCredentialsException("Phiên đăng nhập đã hết hạn");
        }

        Account account = dbToken.getAccount();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new DisabledException("Tài khoản đã bị vô hiệu hóa");
        }

        String newAccessToken = jwtUtil.generateAccessToken(account);

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .accountId(account.getId())
                .build();
    }

    @Override
    public void logout(String refreshTokenStr) {
        if (refreshTokenStr == null) return;

        refreshTokenRepository
                .findByToken(refreshTokenStr)
                .filter(token -> !Boolean.TRUE.equals(token.getRevoked()))
                .ifPresent(
                        token -> {
                            token.setRevoked(true);
                            refreshTokenRepository.save(token);
                            Account account = token.getAccount();
                            auditLogService.record(
                                    AuditEvent.builder()
                                            .entityType("Account")
                                            .actionType(AuditAction.LOGOUT)
                                            .performedByAccountId(account.getId())
                                            .restaurantId(
                                                    account.getRestaurant() == null
                                                            ? null
                                                            : account.getRestaurant().getId())
                                            .reason("Đăng xuất")
                                            .build());
                        });
    }

    /**
     * Ghi nhật ký một lần đăng nhập (FR-ADM-04, FR-LOG-01).
     *
     * TUYỆT ĐỐI không ghi mật khẩu — nhật ký là dữ liệu mọi quản trị viên đọc được.
     * Chỉ ghi tên đăng nhập đã nhập và mã nhà hàng.
     */
    private void recordLoginAttempt(
            LoginRequest request, Account account, boolean success, String failureReason) {
        String attempted =
                "restaurantCode="
                        + request.getRestaurantCode()
                        + "; username="
                        + request.getUsername();
        auditLogService.record(
                AuditEvent.builder()
                        .entityType("Account")
                        .entityId(account != null ? account.getId() : null)
                        .actionType(AuditAction.LOGIN)
                        .restaurantId(
                                account != null && account.getRestaurant() != null
                                        ? account.getRestaurant().getId()
                                        : null)
                        .performedByAccountId(account != null ? account.getId() : null)
                        .valueAfter(
                                success
                                        ? "result=SUCCESS; " + attempted
                                        : "result=FAILURE; " + attempted)
                        .reason(
                                success
                                        ? "Đăng nhập thành công"
                                        : "Đăng nhập thất bại: " + failureReason)
                        .build());
    }
}
