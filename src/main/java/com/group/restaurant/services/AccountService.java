package com.group.restaurant.services;

import com.group.restaurant.dtos.request.account.AccountAddRequest;
import com.group.restaurant.dtos.request.account.AccountUpdateRequest;
import com.group.restaurant.dtos.response.account.AccountResponse;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.enums.AccountStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface AccountService {

    AccountResponse createAccount(AccountAddRequest request);

    AccountResponse updateAccount(Long id, AccountUpdateRequest request);

    AccountResponse getAccountById(Long id);

    AccountResponse getAccountByUsername(String username);

    PageResponse<AccountResponse> getAllAccounts(
            Long restaurantId, AccountStatus status, Pageable pageable);

    List<AccountResponse> getAccountsByRestaurant(Long restaurantId);

    List<AccountResponse> getPlatformAdmins();

    void changeStatus(Long id, AccountStatus status);

    void changePassword(Long id, String oldPassword, String newPassword);

    void resetPassword(Long id, String newPassword);

    void deleteAccount(Long id);
}
