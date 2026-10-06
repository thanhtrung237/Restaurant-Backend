package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.account.AccountAddRequest;
import com.group.restaurant.dtos.request.account.AccountUpdateRequest;
import com.group.restaurant.dtos.response.account.AccountResponse;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.enums.AccountStatus;
import com.group.restaurant.services.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // Quản trị nhà hàng quản lý nhân viên (FR-ADM-01)
    @PostMapping("")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<AccountResponse>> createAccount(
            @Valid @RequestBody AccountAddRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED, "Tạo tài khoản thành công", accountService.createAccount(request)
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<AccountResponse>> updateAccount(
            @PathVariable Long id, @Valid @RequestBody AccountUpdateRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK, "Cập nhật tài khoản thành công", accountService.updateAccount(id, request)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")  // Cho phép mọi user đã đăng nhập
    public ResponseEntity<ResponseSuccess<AccountResponse>> getAccountById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK, "Lấy tài khoản thành công", accountService.getAccountById(id)
        ));
    }

    @GetMapping("/username/{username}")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<AccountResponse>> getAccountByUsername(@PathVariable String username) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK, "Lấy tài khoản thành công", accountService.getAccountByUsername(username)
        ));
    }

    @GetMapping("")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<PageResponse<AccountResponse>>> getAllAccounts(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK, "Lấy danh sách tài khoản thành công",
                accountService.getAllAccounts(restaurantId, status, pageable)
        ));
    }

    @GetMapping("/restaurant/{restaurantId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<List<AccountResponse>>> getAccountsByRestaurant(
            @PathVariable Long restaurantId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK, "Lấy danh sách tài khoản theo nhà hàng thành công",
                accountService.getAccountsByRestaurant(restaurantId)
        ));
    }

    // Quản trị nhà hàng vô hiệu hóa tài khoản
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<Void>> changeStatus(
            @PathVariable Long id, @RequestParam AccountStatus status) {
        accountService.changeStatus(id, status);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Thay đổi trạng thái tài khoản thành công", null));
    }

    // Tự đổi mật khẩu (bất kỳ nhân viên nào)
    @PutMapping("/{id}/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseSuccess<Void>> changePassword(
            @PathVariable Long id,
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {
        accountService.changePassword(id, oldPassword, newPassword);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Đổi mật khẩu thành công", null));
    }

    // Quản trị nhà hàng reset mật khẩu hộ (FR-ADM-01)
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<Void>> resetPassword(
            @PathVariable Long id, @RequestParam String newPassword) {
        accountService.resetPassword(id, newPassword);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Reset mật khẩu thành công", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<Void>> deleteAccount(@PathVariable Long id) {
        accountService.deleteAccount(id);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Xóa tài khoản thành công", null));
    }
}
