package com.group.restaurant;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.enums.CategoryStatus;
import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.Assignment;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Category;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.models.Role;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MenuWritePermissionsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager entities;
    private Restaurant restaurant;
    private Category category;
    private Branch ownBranch;
    private Branch otherBranch;

    @BeforeEach
    void prepareMenu() {
        restaurant = new Restaurant();
        restaurant.setCode("MENU-PERMISSIONS-TEST");
        restaurant.setName("Nhà hàng kiểm thử");
        entities.persist(restaurant);
        ownBranch = createBranch("FIRST");
        otherBranch = createBranch("SECOND");
        category =
                Category.builder()
                        .restaurant(restaurant)
                        .name("Khai vị")
                        .status(CategoryStatus.ACTIVE)
                        .build();
        entities.persist(category);
    }

    @Test
    void branchManagerCanOnlyCreateAndChangeDishesInAssignedBranch() throws Exception {
        var branchManager = staff("BRANCH_MANAGER", RoleScope.BRANCH);
        var manager = staff("MANAGER", RoleScope.RESTAURANT);
        long sharedDish = createDish(manager, null);
        long otherDish = createDish(manager, otherBranch.getId());
        long ownDish = createDish(branchManager, ownBranch.getId());

        mvc.perform(withJson(post("/api/v1/dishes"), branchManager, dishBody(null)))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/v1/dishes"), branchManager, dishBody(otherBranch.getId())))
                .andExpect(status().isForbidden());
        mvc.perform(
                        withJson(
                                put("/api/v1/dishes/{id}", sharedDish),
                                branchManager,
                                Map.of("basePrice", 20000)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        withJson(
                                put("/api/v1/dishes/{id}", otherDish),
                                branchManager,
                                Map.of("name", "Đổi tên")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/v1/dishes/{id}/status", sharedDish)
                                .param("status", "INACTIVE")
                                .with(authentication(branchManager)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/dishes/{id}", sharedDish).with(authentication(branchManager)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        withJson(
                                put("/api/v1/dishes/{id}", ownDish),
                                branchManager,
                                Map.of("name", "Món riêng đã sửa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Món riêng đã sửa"));
    }

    @Test
    void branchManagerCanUseSharedToppingsOnlyOnOwnDishes() throws Exception {
        var branchManager = staff("BRANCH_MANAGER", RoleScope.BRANCH);
        var manager = staff("MANAGER", RoleScope.RESTAURANT);
        long sharedDish = createDish(manager, null);
        long ownDish = createDish(branchManager, ownBranch.getId());
        var groupBody =
                Map.of(
                        "restaurantId",
                        restaurant.getId(),
                        "name",
                        "Topping dùng chung",
                        "selectionType",
                        "OPTIONAL",
                        "minSelect",
                        0,
                        "maxSelect",
                        2);
        var groupResult =
                mvc.perform(withJson(post("/api/v1/option-groups"), manager, groupBody))
                        .andExpect(status().isCreated())
                        .andReturn();
        long groupId =
                json.readTree(groupResult.getResponse().getContentAsString())
                        .path("data")
                        .path("id")
                        .asLong();

        mvc.perform(withJson(post("/api/v1/option-groups"), branchManager, groupBody))
                .andExpect(status().isForbidden());
        mvc.perform(
                        withJson(
                                put("/api/v1/option-groups/{id}", groupId),
                                branchManager,
                                Map.of("name", "Khác")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        withJson(
                                post("/api/v1/option-items"),
                                branchManager,
                                Map.of(
                                        "optionGroupId",
                                        groupId,
                                        "name",
                                        "Trân châu",
                                        "extraPrice",
                                        5000)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        withJson(
                                post("/api/v1/dish-option-groups"),
                                branchManager,
                                Map.of("dishId", sharedDish, "optionGroupId", groupId)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        withJson(
                                post("/api/v1/dish-option-groups"),
                                branchManager,
                                Map.of("dishId", ownDish, "optionGroupId", groupId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.optionGroupId").value(groupId));
    }

    @Test
    void canRecreateDeletedOverrideWithNewPriceAndVisibility() throws Exception {
        var branchManager = staff("BRANCH_MANAGER", RoleScope.BRANCH);
        long dishId = createDish(staff("MANAGER", RoleScope.RESTAURANT), null);
        String endpoint = "/api/v1/dish-overrides";
        var originalBody =
                Map.of(
                        "branchId",
                        ownBranch.getId(),
                        "dishId",
                        dishId,
                        "overridePrice",
                        18000,
                        "isHidden",
                        true);
        var original =
                mvc.perform(withJson(post(endpoint), branchManager, originalBody))
                        .andExpect(status().isCreated())
                        .andReturn();
        long overrideId =
                json.readTree(original.getResponse().getContentAsString())
                        .path("data")
                        .path("id")
                        .asLong();

        mvc.perform(delete(endpoint + "/{id}", overrideId).with(authentication(branchManager)))
                .andExpect(status().isOk());
        entities.flush();
        entities.clear();
        mvc.perform(
                        get(endpoint + "/branch/{id}", ownBranch.getId())
                                .with(authentication(branchManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        var newBody =
                Map.of("branchId", ownBranch.getId(), "dishId", dishId, "overridePrice", 200000);
        mvc.perform(withJson(post(endpoint), branchManager, newBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(overrideId))
                .andExpect(jsonPath("$.data.overridePrice").value(200000))
                .andExpect(jsonPath("$.data.isHidden").value(false));
        entities.flush();
        entities.clear();
        mvc.perform(
                        get(endpoint + "/branch/{id}", ownBranch.getId())
                                .with(authentication(branchManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].overridePrice").value(200000));
        mvc.perform(withJson(post(endpoint), branchManager, originalBody))
                .andExpect(status().isConflict());
    }

    private Branch createBranch(String code) {
        Branch branch = new Branch();
        branch.setRestaurant(restaurant);
        branch.setCode(code);
        branch.setName(code);
        entities.persist(branch);
        return branch;
    }

    @Test
    void branchMenuSerializesStatusesAndFiltersCustomerVisibility() throws Exception {
        var manager = staff("MANAGER", RoleScope.RESTAURANT);
        var branchManager = staff("BRANCH_MANAGER", RoleScope.BRANCH);
        long shared = createDish(manager, null);
        long local = createDish(branchManager, ownBranch.getId());
        long otherLocal = createDish(manager, otherBranch.getId());
        var hiddenOverride =
                mvc.perform(
                                withJson(
                                        post("/api/v1/dish-overrides"),
                                        branchManager,
                                        Map.of(
                                                "branchId",
                                                ownBranch.getId(),
                                                "dishId",
                                                shared,
                                                "isHidden",
                                                true,
                                                "overridePrice",
                                                20000)))
                        .andExpect(status().isCreated())
                        .andReturn();
        long overrideId =
                json.readTree(hiddenOverride.getResponse().getContentAsString())
                        .path("data")
                        .path("id")
                        .asLong();
        mvc.perform(
                        withJson(
                                put("/api/v1/dish-overrides/{id}", overrideId),
                                branchManager,
                                Map.of("clearOverridePrice", true)))
                .andExpect(status().isOk());
        mvc.perform(
                        put(
                                        "/api/v1/branch-dish-stocks/branch/{branchId}/dish/{dishId}/toggle",
                                        ownBranch.getId(),
                                        local)
                                .with(authentication(branchManager)))
                .andExpect(status().isOk());

        String overview = "/api/v1/branches/{id}/menu/overview";
        String menu = "/api/v1/branches/{id}/menu";
        mvc.perform(get(overview, ownBranch.getId()).with(authentication(branchManager)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data[?(@.dishId == " + shared + ")].isHidden")
                                .value(contains(true)))
                .andExpect(
                        jsonPath("$.data[?(@.dishId == " + shared + ")].hasOverride")
                                .value(contains(false)))
                .andExpect(
                        jsonPath("$.data[?(@.dishId == " + local + ")].isBranchSpecific")
                                .value(contains(true)))
                .andExpect(
                        jsonPath("$.data[?(@.dishId == " + local + ")].stockStatus")
                                .value(contains("OUT_OF_STOCK")))
                .andExpect(jsonPath("$.data[?(@.dishId == " + otherLocal + ")]").isEmpty());
        mvc.perform(get(menu, ownBranch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mvc.perform(
                        put("/api/v1/dishes/{id}/status", local)
                                .param("status", "INACTIVE")
                                .with(authentication(branchManager)))
                .andExpect(status().isOk());
        mvc.perform(get(overview, ownBranch.getId()).with(authentication(branchManager)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data[?(@.dishId == " + local + ")].isHidden")
                                .value(contains(true)));
        mvc.perform(
                        withJson(
                                put("/api/v1/dish-overrides/{id}", overrideId),
                                branchManager,
                                Map.of("isHidden", false)))
                .andExpect(status().isOk());
        mvc.perform(get(menu, ownBranch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dishId").value(shared))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    private long createDish(UsernamePasswordAuthenticationToken auth, Long branchId)
            throws Exception {
        var response =
                mvc.perform(withJson(post("/api/v1/dishes"), auth, dishBody(branchId)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse();
        return json.readTree(response.getContentAsString()).path("data").path("id").asLong();
    }

    private Map<String, Object> dishBody(Long branchId) {
        Map<String, Object> body =
                new HashMap<>(
                        Map.of(
                                "restaurantId",
                                restaurant.getId(),
                                "categoryId",
                                category.getId(),
                                "name",
                                "Món kiểm thử",
                                "basePrice",
                                15000));
        if (branchId != null) body.put("branchId", branchId);
        return body;
    }

    private MockHttpServletRequestBuilder withJson(
            MockHttpServletRequestBuilder request,
            UsernamePasswordAuthenticationToken auth,
            Object body)
            throws Exception {
        return request.with(authentication(auth))
                .contentType("application/json")
                .content(json.writeValueAsString(body));
    }

    private UsernamePasswordAuthenticationToken staff(String code, RoleScope scope) {
        Role role = Role.builder().code(code).scope(scope).build();
        Account account =
                Account.builder().id(901L).username("menu-test").restaurant(restaurant).build();
        account.setAssignments(
                List.of(
                        Assignment.builder()
                                .account(account)
                                .branch(ownBranch)
                                .role(role)
                                .build()));
        AccountUserDetails principal = new AccountUserDetails(account);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
