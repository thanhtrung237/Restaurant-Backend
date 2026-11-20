package com.group.restaurant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.Assignment;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.DiningTable;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.models.Role;
import com.group.restaurant.models.TableSession;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BranchAreaIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager entities;

    @Test
    void createsAreasWithinBranchScopeAndKeepsTablesInTheirOwnBranch() throws Exception {
        Restaurant restaurant = new Restaurant();
        restaurant.setCode("AREA-TEST");
        restaurant.setName("Nhà hàng kiểm thử khu vực");
        entities.persist(restaurant);
        Branch first = branch(restaurant, "FIRST");
        Branch second = branch(restaurant, "SECOND");
        var branchManager = staff(first, "BRANCH_MANAGER", RoleScope.BRANCH);
        var restaurantAdmin = staff(first, "MANAGER", RoleScope.RESTAURANT);
        var cashier = staff(first, "CASHIER", RoleScope.BRANCH);

        call(post("/api/v1/areas"), cashier, Map.of("branchId", first.getId(), "name", "Sân thượng"))
                .andExpect(status().isForbidden());
        call(post("/api/v1/areas"), branchManager, Map.of("branchId", second.getId(), "name", "Tầng 2"))
                .andExpect(status().isForbidden());
        long areaId = createdId(call(post("/api/v1/areas"), branchManager,
                Map.of("branchId", first.getId(), "name", "  Tầng 2  "))
                .andExpect(jsonPath("$.data.name").value("Tầng 2")));
        long otherAreaId = createdId(call(post("/api/v1/areas"), restaurantAdmin,
                Map.of("branchId", second.getId(), "name", "Tầng 2")));

        call(post("/api/v1/tables"), branchManager,
                Map.of("branchId", first.getId(), "name", "Bàn 01", "capacity", 4, "areaId", otherAreaId))
                .andExpect(status().isNotFound());
        long tableId = createdId(call(post("/api/v1/tables"), branchManager,
                Map.of("branchId", first.getId(), "name", "Bàn 01", "capacity", 4, "areaId", areaId)));
        call(put("/api/v1/tables/{id}", tableId), branchManager, Map.of("areaId", otherAreaId))
                .andExpect(status().isNotFound());
        call(post("/api/v1/areas"), branchManager, Map.of("branchId", first.getId(), "name", "tầng 2"))
                .andExpect(status().isConflict());
    }

    private Branch branch(Restaurant restaurant, String code) {
        Branch branch = new Branch();
        branch.setRestaurant(restaurant);
        branch.setCode(code);
        branch.setName(code);
        entities.persist(branch);
        return branch;
    }

    @Test
    void protectsSessionsAndOnlyDeletesUnusedTablesAndEmptyAreas() throws Exception {
        Restaurant restaurant = new Restaurant();
        restaurant.setCode("AREA-REMOVAL-TEST");
        restaurant.setName("Nhà hàng kiểm thử");
        entities.persist(restaurant);
        Branch first = branch(restaurant, "FIRST");
        Branch second = branch(restaurant, "SECOND");
        var manager = staff(first, "BRANCH_MANAGER", RoleScope.BRANCH);
        var otherManager = staff(second, "BRANCH_MANAGER", RoleScope.BRANCH);
        var areaBody = Map.of("branchId", first.getId(), "name", "Tầng 2");
        long areaId = createdId(call(post("/api/v1/areas"), manager, areaBody));
        var historyTable = Map.of("branchId", first.getId(), "name", "Bàn A", "areaId", areaId, "qrCode", "AREA-HISTORY");
        long usedId = createdId(call(post("/api/v1/tables"), manager, historyTable));
        var unusedTable = Map.of("branchId", first.getId(), "name", "Bàn B", "areaId", areaId, "qrCode", "AREA-UNUSED");
        long unusedId = createdId(call(post("/api/v1/tables"), manager, unusedTable));
        var session = TableSession.builder().table(entities.find(DiningTable.class, usedId))
                .branch(first).status(TableSessionStatus.RESERVED).build();
        entities.persist(session);

        mvc.perform(delete("/api/v1/tables/{id}", unusedId).with(authentication(otherManager))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/areas/{id}", areaId).with(authentication(otherManager))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/areas/{id}", areaId).with(authentication(manager))).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/tables/{id}", usedId).with(authentication(manager))).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/tables/{id}/status", usedId).param("status", "INACTIVE").with(authentication(manager)))
                .andExpect(status().isConflict());

        session.setStatus(TableSessionStatus.CLOSED);
        entities.flush();
        mvc.perform(delete("/api/v1/tables/{id}", usedId).with(authentication(manager))).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/tables/{id}/status", usedId).param("status", "INACTIVE").with(authentication(manager)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/guest/table").param("branchId", first.getId().toString()).param("qrCode", "AREA-HISTORY"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/tables/{id}/status", usedId).param("status", "AVAILABLE").with(authentication(manager)))
                .andExpect(status().isOk());
        call(put("/api/v1/tables/{id}", usedId), manager, Map.of("clearArea", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.areaId").doesNotExist());
        mvc.perform(delete("/api/v1/tables/{id}", unusedId).with(authentication(manager))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/areas/{id}", areaId).with(authentication(manager))).andExpect(status().isOk());
        entities.flush();
        entities.clear();
        mvc.perform(get("/api/v1/guest/table").param("branchId", first.getId().toString()).param("qrCode", "AREA-UNUSED"))
                .andExpect(status().isNotFound());
        call(post("/api/v1/tables"), manager, unusedTable).andExpect(status().isConflict());
        call(post("/api/v1/areas"), manager, areaBody).andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(areaId));
        mvc.perform(get("/api/v1/table-sessions/{id}", session.getId()).with(authentication(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tableName").value("Bàn A"));
    }

    private UsernamePasswordAuthenticationToken staff(Branch branch, String code, RoleScope scope) {
        Role role = Role.builder().code(code).scope(scope).build();
        Account account = Account.builder().id(901L).username("area-test").restaurant(branch.getRestaurant()).build();
        account.setAssignments(List.of(Assignment.builder().account(account).branch(branch).role(role).build()));
        AccountUserDetails principal = new AccountUserDetails(account);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, UsernamePasswordAuthenticationToken auth,
                               Object body) throws Exception {
        return mvc.perform(request.with(authentication(auth))
                .contentType("application/json").content(json.writeValueAsString(body)));
    }

    private long createdId(ResultActions result) throws Exception {
        var response = result.andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("data").path("id").asLong();
    }
}
