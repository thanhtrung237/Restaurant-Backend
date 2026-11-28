package com.group.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group.restaurant.configurations.RestaurantRoleInitialization;
import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.dtos.request.restaurant.RestaurantAddRequest;
import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.models.Role;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.repositories.RoleRepository;
import com.group.restaurant.services.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RestaurantDefaultRolesIntegrationTest {
    @Autowired RestaurantService restaurantService;
    @Autowired RestaurantRepository restaurants;
    @Autowired RoleRepository roles;
    @Autowired RestaurantRoleInitialization initialization;
    @Autowired MockMvc mvc;

    @Test
    void newRestaurantHasAllAssignableRolesAndLooksThemUpWithinItsOwnTenant() throws Exception {
        var first = createRestaurant("NEW-ROLES-1");
        var second = createRestaurant("NEW-ROLES-2");
        assertDefaultRoles(first.getId());
        assertDefaultRoles(second.getId());
        var secondCashier =
                roles.findByRestaurantIdAndCode(second.getId(), "CASHIER").orElseThrow();
        var principal = new AccountUserDetails(Account.builder().restaurant(second).build());
        var auth =
                new UsernamePasswordAuthenticationToken(
                        principal, null, AuthorityUtils.createAuthorityList("ROLE_MANAGER"));

        mvc.perform(get("/api/v1/roles/restaurant/{id}", second.getId()).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
        mvc.perform(get("/api/v1/roles/code/CASHIER").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(secondCashier.getId()));
        mvc.perform(get("/api/v1/roles/restaurant/{id}", first.getId()).with(authentication(auth)))
                .andExpect(status().isForbidden());
    }

    @Test
    void startupBackfillsMissingRolesWithoutReplacingExistingOnesOrCreatingDuplicates() {
        var legacy = new Restaurant();
        legacy.setCode("LEGACY-ROLES");
        legacy.setName("Legacy roles");
        restaurants.save(legacy);
        var manager =
                roles.save(
                        Role.builder()
                                .restaurant(legacy)
                                .scope(RoleScope.RESTAURANT)
                                .code("MANAGER")
                                .name("Chủ nhà hàng")
                                .build());

        initialization.run(new DefaultApplicationArguments());
        initialization.run(new DefaultApplicationArguments());

        assertDefaultRoles(legacy.getId());
        var existingManager =
                roles.findByRestaurantIdAndCode(legacy.getId(), "MANAGER").orElseThrow();
        assertThat(existingManager.getId()).isEqualTo(manager.getId());
        assertThat(existingManager.getName()).isEqualTo("Chủ nhà hàng");
    }

    private Restaurant createRestaurant(String code) {
        var request = new RestaurantAddRequest();
        request.setCode(code);
        request.setName(code);
        return restaurants
                .findById(restaurantService.createRestaurant(request).getId())
                .orElseThrow();
    }

    private void assertDefaultRoles(Long restaurantId) {
        assertThat(roles.findByRestaurantId(restaurantId))
                .extracting(Role::getCode, Role::getScope)
                .containsExactlyInAnyOrder(
                        tuple("MANAGER", RoleScope.RESTAURANT),
                        tuple("BRANCH_MANAGER", RoleScope.BRANCH),
                        tuple("CASHIER", RoleScope.BRANCH),
                        tuple("WAITER", RoleScope.BRANCH));
    }
}
