package com.group.restaurant.configurations.jwt;

import com.group.restaurant.models.Account;
import com.group.restaurant.repositories.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nạp {@link AccountUserDetails} cho Spring Security.
 *
 * LÝ DO TỒN TẠI HÀM {@link #loadByAccountId(Long)}:
 * `username` CHỈ unique trong phạm vi 1 nhà hàng
 * (`uk_account_restaurant_username = (restaurant_id, username)`), nghĩa là nhà hàng A và
 * nhà hàng B đều có thể có tài khoản "admin".
 *
 * Trước đây {@code AuthTokenFilter} nhận diện người dùng bằng CHÍNH `subject` (username)
 * của JWT rồi gọi `loadUserByUsername(...)`. Với 2 tài khoản trùng username ở 2 nhà hàng:
 *  - Tốt nhất: query trả về 2 hàng cho một `Optional` → ném lỗi → mọi request 500.
 *  - Xấu nhất: nhận diện SANG tài khoản của nhà hàng khác — vượt tenant ngay ở tầng
 *    xác thực, khiến mọi kiểm tra phân quyền/tenant phía sau trở nên vô nghĩa.
 *
 * JWT đã mang sẵn claim `accountId` (xem {@code JwtUtil#generateAccessToken}), nên nhận
 * diện phải dựa vào ID — khóa chính, không nhập nhằng.
 */
@Service
@RequiredArgsConstructor
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    /**
     * Chỉ dùng cho {@code DaoAuthenticationProvider}. Luồng đăng nhập thật của hệ thống
     * là {@code AuthServiceImpl#login}, tìm theo (restaurantCode, username) nên không
     * gặp nhập nhằng.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByUsernameWithAssignments(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản: " + username));
        return new AccountUserDetails(account);
    }

    /** Nhận diện theo khóa chính — dùng cho mọi request đã có access token. */
    @Transactional(readOnly = true)
    public UserDetails loadByAccountId(Long accountId) {
        Account account = accountRepository.findByIdForAuthentication(accountId)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản ID: " + accountId));
        return new AccountUserDetails(account);
    }
}
