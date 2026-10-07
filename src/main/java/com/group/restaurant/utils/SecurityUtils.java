package com.group.restaurant.utils;

import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.models.Account;
import com.group.restaurant.repositories.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final AccountRepository accountRepository;

    public Account getCurrentAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResourceNotFoundException("Chưa đăng nhập");
        }
        AccountUserDetails userDetails = (AccountUserDetails) auth.getPrincipal();
        return accountRepository.findByIdWithAssignments(userDetails.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

    public Long getCurrentAccountId() {
        AccountUserDetails userDetails = (AccountUserDetails)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userDetails.getAccountId();
    }

    public Long getCurrentRestaurantId() {
        AccountUserDetails userDetails = (AccountUserDetails)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userDetails.getRestaurantId();
    }
}
