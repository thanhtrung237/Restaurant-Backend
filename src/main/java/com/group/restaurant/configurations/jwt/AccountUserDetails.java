package com.group.restaurant.configurations.jwt;

import com.group.restaurant.enums.AccountStatus;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.Assignment;
import java.util.Collection;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class AccountUserDetails implements UserDetails {

    private final Account account;

    public AccountUserDetails(Account account) {
        this.account = account;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        java.util.Set<GrantedAuthority> authorities = new java.util.HashSet<>();
        if (account.getAssignments() != null) {
            authorities.addAll(
                    account.getAssignments().stream()
                            .filter(a -> a.getRole() != null && a.getRole().getCode() != null)
                            .map(Assignment::getRole)
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                            .collect(Collectors.toSet()));
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return account.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return account.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return account.getStatus() == AccountStatus.ACTIVE;
    }

    public Long getAccountId() {
        return account.getId();
    }

    public Long getRestaurantId() {
        return account.getRestaurant() != null ? account.getRestaurant().getId() : null;
    }
}
