package com.group.restaurant.utils;

import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.models.Account;
import com.group.restaurant.repositories.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Tra cứu "tôi là ai" từ SecurityContext.
 *
 * Để KIỂM TRA QUYỀN theo nhà hàng/chi nhánh, dùng {@link TenantGuard} — class này chỉ
 * trả về thông tin, không phán quyết.
 *
 * Trước đây các hàm ở đây ép kiểu thẳng `(AccountUserDetails) auth.getPrincipal()`.
 * Với request ẩn danh (luồng khách quét QR) principal là chuỗi "anonymousUser", nên
 * mỗi lời gọi sẽ ném ClassCastException → 500. Class không được service nào dùng nên
 * lỗi này chưa lộ ra; đã siết lại để khi bắt đầu dùng thì không sập.
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final AccountRepository accountRepository;

    /** Tài khoản đang đăng nhập, ném lỗi nếu request là ẩn danh. */
    public Account getCurrentAccount() {
        AccountUserDetails details = currentUserDetails();
        if (details == null) {
            throw new ResourceNotFoundException("Chưa đăng nhập");
        }
        return accountRepository.findByIdWithAssignments(details.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

    /** ID tài khoản đang đăng nhập, hoặc null nếu ẩn danh. */
    public Long getCurrentAccountId() {
        AccountUserDetails details = currentUserDetails();
        return details != null ? details.getAccountId() : null;
    }

    /** ID nhà hàng của người đang đăng nhập, hoặc null nếu ẩn danh. */
    public Long getCurrentRestaurantId() {
        AccountUserDetails details = currentUserDetails();
        return details != null ? details.getRestaurantId() : null;
    }

    private AccountUserDetails currentUserDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || !(auth.getPrincipal() instanceof AccountUserDetails details)) {
            return null;
        }
        return details;
    }
}
