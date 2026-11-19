package com.group.restaurant;

import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.models.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperationalPermissionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager entities;

    @Test
    void grantsAndRevokesReservationOnAssignedBranchOnly() throws Exception {
        var restaurant = restaurant("PERMISSIONS-A");
        var otherRestaurant = restaurant("PERMISSIONS-B");
        var branch = branch(restaurant, "A1");
        var otherBranch = branch(restaurant, "A2");
        var foreignBranch = branch(otherRestaurant, "B1");
        var firstTable = table(branch, "Bàn 1");
        var secondTable = table(branch, "Bàn 2");
        var outsideTable = table(otherBranch, "Bàn 3");
        var foreignTable = table(foreignBranch, "Bàn 4");
        var manager = staff(restaurant, null, "MANAGER", RoleScope.RESTAURANT);
        var cashier = staff(restaurant, branch, "CASHIER", RoleScope.BRANCH);
        var foreignCashier = staff(otherRestaurant, foreignBranch, "CASHIER", RoleScope.BRANCH);
        entities.flush();

        reserve(cashier, firstTable).andExpect(status().isForbidden());
        update(cashier, "[]", "[\"RESERVE_TABLE\"]").andExpect(status().isForbidden());
        update(manager, "[]", "[\"RESERVE_TABLE\"]").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeCount").value(1));
        entities.flush();
        entities.clear();

        Long cashierId = ((AccountUserDetails) cashier.getPrincipal()).getAccountId();
        mvc.perform(get("/api/v1/operational-permissions/employees/{id}", cashierId).with(authentication(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].branchId").value(branch.getId()))
                .andExpect(jsonPath("$.data[0].permissions[0].code").value("RESERVE_TABLE"));
        reserve(cashier, firstTable).andExpect(status().isCreated());
        reserve(cashier, outsideTable).andExpect(status().isForbidden());
        reserve(cashier, foreignTable).andExpect(status().isForbidden());
        reserve(foreignCashier, foreignTable).andExpect(status().isForbidden());
        update(manager, "[]", "[]").andExpect(status().isConflict());
        update(manager, "[\"RESERVE_TABLE\"]", "[]").andExpect(status().isOk());
        reserve(cashier, secondTable).andExpect(status().isForbidden());
    }

    private ResultActions update(UsernamePasswordAuthenticationToken staff, String expected, String enabled) throws Exception {
        return mvc.perform(put("/api/v1/operational-permissions/roles/CASHIER").with(authentication(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expected\":%s,\"enabled\":%s}".formatted(expected, enabled)));
    }

    @Test
    void savesMatrixChangesTogetherAndRejectsStaleBatches() throws Exception {
        var restaurant = restaurant("PERMISSIONS-MATRIX");
        var branch = branch(restaurant, "M1");
        var manager = staff(restaurant, null, "MANAGER", RoleScope.RESTAURANT);
        var cashier = staff(restaurant, branch, "CASHIER", RoleScope.BRANCH);
        var waiter = staff(restaurant, branch, "WAITER", RoleScope.BRANCH);
        entities.flush();

        String stale = """
                {"changes":[
                  {"role":"CASHIER","expected":[],"enabled":["RESERVE_TABLE"]},
                  {"role":"WAITER","expected":[],"enabled":[]}
                ]}
                """;
        updateRoles(cashier, stale).andExpect(status().isForbidden());
        updateRoles(manager, stale).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/operational-permissions/branches/{id}/me", branch.getId())
                        .with(authentication(cashier)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());

        updateRoles(manager, """
                {"changes":[
                  {"role":"CASHIER","expected":[],"enabled":["RESERVE_TABLE"]},
                  {"role":"WAITER","expected":["RESERVE_TABLE","CONFIRM_ORDER","WEIGH_ORDER","SERVE_ORDER"],
                   "enabled":["CONFIRM_ORDER","WEIGH_ORDER","SERVE_ORDER"]}
                ]}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].enabled[0]").value("RESERVE_TABLE"))
                .andExpect(jsonPath("$.data[1].enabled.length()").value(3));
        reserve(waiter, table(branch, "Bàn ma trận")).andExpect(status().isForbidden());
        reserve(cashier, table(branch, "Bàn thu ngân")).andExpect(status().isCreated());
    }

    private ResultActions updateRoles(UsernamePasswordAuthenticationToken staff, String body) throws Exception {
        return mvc.perform(put("/api/v1/operational-permissions/roles").with(authentication(staff))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions reserve(UsernamePasswordAuthenticationToken staff, DiningTable table) throws Exception {
        return mvc.perform(post("/api/v1/table-sessions/reserve").with(authentication(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tableId":%d,"reservationName":"Khách test","reservationPhone":"0900000000",
                        "reservationTime":"%s","guestCount":2}
                        """.formatted(table.getId(), LocalDateTime.now().plusHours(2))));
    }

    private Restaurant restaurant(String code) {
        var restaurant = new Restaurant();
        restaurant.setCode(code);
        restaurant.setName(code);
        entities.persist(restaurant);
        return restaurant;
    }

    private Branch branch(Restaurant restaurant, String code) {
        var branch = new Branch();
        branch.setRestaurant(restaurant);
        branch.setCode(code);
        branch.setName(code);
        entities.persist(branch);
        return branch;
    }

    private DiningTable table(Branch branch, String name) {
        var table = DiningTable.builder().branch(branch).name(name).status(DiningTableStatus.AVAILABLE).build();
        entities.persist(table);
        return table;
    }

    private UsernamePasswordAuthenticationToken staff(Restaurant restaurant, Branch branch, String code, RoleScope scope) {
        var role = Role.builder().restaurant(restaurant).code(code).name(code).scope(scope).build();
        entities.persist(role);
        var account = Account.builder().restaurant(restaurant).username(code).employeeCode(code)
                .fullName(code).passwordHash("integration-test").assignments(new ArrayList<>()).build();
        account.getAssignments().add(Assignment.builder().account(account).branch(branch).role(role).build());
        entities.persist(account);
        var principal = new AccountUserDetails(account);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
