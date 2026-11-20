package com.group.restaurant;

import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.models.Account;
import com.group.restaurant.models.Assignment;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.models.Role;
import com.group.restaurant.repositories.AccountRepository;
import com.group.restaurant.repositories.AssignmentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BranchStaffIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager entities;
    @Autowired AccountRepository accounts;
    @Autowired AssignmentRepository assignments;

    @Test
    void createsAndReassignsStaffWithinManagedBranch() throws Exception {
        Restaurant restaurant = restaurant("STAFF-LOCAL");
        Branch local = branch(restaurant, "STAFF-01");
        Branch outside = branch(restaurant, "STAFF-02");
        Branch foreign = branch(restaurant("STAFF-FOREIGN"), "STAFF-03");
        Role manager = role(restaurant, "BRANCH_MANAGER", RoleScope.BRANCH);
        Role waiter = role(restaurant, "WAITER", RoleScope.BRANCH);
        role(restaurant, "CASHIER", RoleScope.BRANCH);
        Role owner = role(restaurant, "MANAGER", RoleScope.RESTAURANT);
        Account actor = account(restaurant, "manager", manager, local);
        Account remoteEmployee = account(restaurant, "remote", waiter, outside);
        Account protectedEmployee = account(restaurant, "owner", owner, null);
        assign(protectedEmployee, waiter, local);
        var auth = auth(actor);
        entities.flush();
        String path = "/api/v1/branches/{branchId}/staff";
        String create = """
                {"fullName":"Nhân viên mới","username":"new_staff","password":"Start789!","roles":["WAITER"]}
                """;

        mvc.perform(get(path, outside.getId()).with(authentication(auth))).andExpect(status().isForbidden());
        mvc.perform(get(path, foreign.getId()).with(authentication(auth))).andExpect(status().isForbidden());
        mvc.perform(get(path, outside.getId()).with(authentication(auth(remoteEmployee)))).andExpect(status().isForbidden());
        mvc.perform(post(path, outside.getId()).with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
                .content(create)).andExpect(status().isForbidden());
        mvc.perform(post(path, local.getId()).with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
                .content(create.replace("WAITER", "MANAGER"))).andExpect(status().isBadRequest());
        assertThat(accounts.findByRestaurantIdAndUsername(restaurant.getId(), "new_staff")).isEmpty();

        mvc.perform(post(path, local.getId()).with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
                .content(create)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles[0]").value("WAITER"))
                .andExpect(jsonPath("$.data.editable").value(true));
        Account created = accounts.findByRestaurantIdAndUsername(restaurant.getId(), "new_staff").orElseThrow();
        assertThat(new BCryptPasswordEncoder().matches("Start789!", created.getPasswordHash())).isTrue();
        assertThat(assignments.findByAccountId(created.getId())).singleElement()
                .satisfies(assignment -> assertThat(assignment.getBranch().getId()).isEqualTo(local.getId()));
        assign(created, waiter, outside);
        entities.flush();

        String update = "{\"expected\":[\"WAITER\"],\"roles\":[\"CASHIER\"]}";
        mvc.perform(put(path + "/{accountId}/roles", local.getId(), remoteEmployee.getId())
                .with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isForbidden());
        mvc.perform(put(path + "/{accountId}/roles", local.getId(), protectedEmployee.getId())
                .with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isForbidden());
        mvc.perform(put(path + "/{accountId}/roles", local.getId(), created.getId())
                .with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.roles[0]").value("CASHIER"));
        mvc.perform(put(path + "/{accountId}/roles", local.getId(), created.getId())
                .with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isConflict());
        mvc.perform(get(path, local.getId()).with(authentication(auth))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        entities.flush();
        assertThat(assignments.findByAccountId(created.getId())).extracting(a -> a.getBranch().getCode() + ":" + a.getRole().getCode())
                .containsExactlyInAnyOrder("STAFF-01:CASHIER", "STAFF-02:WAITER");
        assertThat(entities.createQuery("""
                select count(log) from AuditLog log
                where log.entityType = 'BranchStaffAssignment' and log.entityId = :id and log.branch.id = :branchId
                """, Long.class).setParameter("id", created.getId()).setParameter("branchId", local.getId()).getSingleResult())
                .isEqualTo(2L);
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

    private Role role(Restaurant restaurant, String code, RoleScope scope) {
        var result = Role.builder().restaurant(restaurant).code(code).name(code).scope(scope).build();
        entities.persist(result);
        return result;
    }

    private Account account(Restaurant restaurant, String username, Role role, Branch branch) {
        var account = Account.builder().restaurant(restaurant).username(username).employeeCode(username)
                .fullName(username).passwordHash("test").build();
        entities.persist(account);
        assign(account, role, branch);
        return account;
    }

    private void assign(Account account, Role role, Branch branch) {
        var assignment = Assignment.builder().account(account).branch(branch).role(role).build();
        entities.persist(assignment);
        account.getAssignments().add(assignment);
    }

    private UsernamePasswordAuthenticationToken auth(Account account) {
        var principal = new AccountUserDetails(account);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
