package com.group.restaurant.services;

import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.models.Role;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.repositories.RoleRepository;
import com.group.restaurant.utils.TenantGuard;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestaurantDefaultRoles {
    private record DefaultRole(String code, String name, RoleScope scope) {}

    private static final List<DefaultRole> DEFAULTS =
            List.of(
                    new DefaultRole(
                            TenantGuard.ROLE_MANAGER, "Quản trị nhà hàng", RoleScope.RESTAURANT),
                    new DefaultRole(
                            TenantGuard.ROLE_BRANCH_MANAGER, "Quản lý chi nhánh", RoleScope.BRANCH),
                    new DefaultRole(TenantGuard.ROLE_CASHIER, "Thu ngân", RoleScope.BRANCH),
                    new DefaultRole(TenantGuard.ROLE_WAITER, "Phục vụ", RoleScope.BRANCH));

    private final RestaurantRepository restaurants;
    private final RoleRepository roles;

    @Transactional
    public void provision(Long restaurantId) {
        var lockedRestaurant = restaurants.findByIdForUpdate(restaurantId);
        if (lockedRestaurant.isEmpty()) return;
        var restaurant = lockedRestaurant.get();

        // Soft-deleted roles still occupy their code. Preserve their deleted state and ID.
        var existingCodes = new HashSet<String>();
        roles.findCodesIncludingDeleted(restaurantId)
                .forEach(code -> existingCodes.add(code.toUpperCase(Locale.ROOT)));

        var missingRoles =
                DEFAULTS.stream()
                        .filter(role -> !existingCodes.contains(role.code()))
                        .map(
                                role ->
                                        Role.builder()
                                                .restaurant(restaurant)
                                                .code(role.code())
                                                .name(role.name())
                                                .scope(role.scope())
                                                .build())
                        .toList();
        if (!missingRoles.isEmpty()) roles.saveAll(missingRoles);
    }
}
