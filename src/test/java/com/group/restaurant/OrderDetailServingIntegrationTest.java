package com.group.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.enums.*;
import com.group.restaurant.models.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderDetailServingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager entities;

    @Test
    void cancelsEmptyCheckoutAndItsDraftOnlyWhenNoMoneyWasCollected() throws Exception {
        var restaurant = new Restaurant();
        restaurant.setCode("EMPTY-CHECKOUT");
        restaurant.setName("Test");
        entities.persist(restaurant);
        var branch = new Branch();
        branch.setRestaurant(restaurant);
        branch.setCode("EMPTY");
        branch.setName("Test");
        entities.persist(branch);
        var table = DiningTable.builder()
                .branch(branch)
                .name("Bàn test")
                .status(DiningTableStatus.OCCUPIED)
                .build();
        entities.persist(table);
        var session = TableSession.builder()
                .table(table)
                .branch(branch)
                .status(TableSessionStatus.WAITING_FOR_PAY)
                .build();
        entities.persist(session);
        var order = Order.builder().tableSession(session).build();
        entities.persist(order);
        var invoice = Invoice.builder()
                .branch(branch)
                .order(order)
                .tableSession(session)
                .invoiceNumber("EMPTY-DRAFT")
                .grandTotal(BigDecimal.ZERO)
                .build();
        entities.persist(invoice);
        var payment = Payment.builder()
                .invoice(invoice)
                .method(PaymentMethod.CASH)
                .amount(BigDecimal.ONE)
                .build();
        entities.persist(payment);

        var account = Account.builder().id(903L).username("cancel-test").restaurant(restaurant).build();
        var role = Role.builder().code("BRANCH_MANAGER").scope(RoleScope.BRANCH).build();
        account.setAssignments(
                List.of(Assignment.builder().account(account).branch(branch).role(role).build()));
        var principal = new AccountUserDetails(account);
        var staff = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var request = post("/api/v1/table-sessions/{id}/cancel", session.getId())
                .with(authentication(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"All lines voided\"}");

        mvc.perform(request).andExpect(status().isConflict());
        assertThat(session.getStatus()).isEqualTo(TableSessionStatus.WAITING_FOR_PAY);
        entities.remove(payment);
        entities.flush();

        mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        entities.flush();
        entities.clear();
        assertThat(entities.find(TableSession.class, session.getId()).getNote())
                .isEqualTo("All lines voided");
        assertThat(entities.find(DiningTable.class, table.getId()).getStatus())
                .isEqualTo(DiningTableStatus.AVAILABLE);
        assertThat(entities.find(Invoice.class, invoice.getId()).getStatus())
                .isEqualTo(InvoiceStatus.CANCELLED);
        mvc.perform(request).andExpect(status().isConflict());
    }

    @Test
    void recordsPartialServingsWithoutChangingOrderQuantityAndRejectsStaleCounts()
            throws Exception {
        var restaurant = new Restaurant();
        restaurant.setCode("SERVING-TEST");
        restaurant.setName("Nhà hàng kiểm thử");
        entities.persist(restaurant);
        var branch = new Branch();
        branch.setRestaurant(restaurant);
        branch.setCode("SERVING");
        branch.setName("Chi nhánh kiểm thử");
        entities.persist(branch);
        var table = DiningTable.builder().branch(branch).name("Bàn 1").build();
        entities.persist(table);
        var session =
                TableSession.builder()
                        .table(table)
                        .branch(branch)
                        .status(TableSessionStatus.SERVING)
                        .build();
        entities.persist(session);
        var order = Order.builder().tableSession(session).build();
        entities.persist(order);
        var category =
                Category.builder()
                        .restaurant(restaurant)
                        .name("Món chính")
                        .status(CategoryStatus.ACTIVE)
                        .build();
        entities.persist(category);
        var price = BigDecimal.valueOf(45000);
        var dish =
                Dish.builder()
                        .restaurant(restaurant)
                        .category(category)
                        .name("Phở")
                        .basePrice(price)
                        .soldByWeight(false)
                        .status(DishStatus.ACTIVE)
                        .build();
        entities.persist(dish);
        var detail =
                OrderDetail.builder()
                        .order(order)
                        .tableSession(session)
                        .dish(dish)
                        .quantity(3)
                        .priceAtOrderTime(price)
                        .price(price.multiply(BigDecimal.valueOf(3)))
                        .status(OrderDetailStatus.PREPARING)
                        .build();
        entities.persist(detail);
        entities.flush();
        entities.clear();

        var role = Role.builder().code("BRANCH_MANAGER").scope(RoleScope.BRANCH).build();
        var account =
                Account.builder().id(902L).username("serving-test").restaurant(restaurant).build();
        account.setAssignments(
                List.of(Assignment.builder().account(account).branch(branch).role(role).build()));
        var principal = new AccountUserDetails(account);
        var staff =
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
        long id = detail.getId();

        serve(id, staff, 0, 0).andExpect(status().isBadRequest());
        serve(id, staff, 1, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PREPARING"))
                .andExpect(jsonPath("$.data.servedQuantity").value(1));
        entities.flush();
        entities.clear();
        mvc.perform(
                        get("/api/v1/order-details/session/{id}", session.getId())
                                .with(authentication(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantity").value(3))
                .andExpect(jsonPath("$.data[0].servedQuantity").value(1))
                .andExpect(jsonPath("$.data[0].price").value(135000));
        serve(id, staff, 1, 0).andExpect(status().isConflict());
        serve(id, staff, 3, 1).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/order-details/{id}/mark-served", id).with(authentication(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SERVED"))
                .andExpect(jsonPath("$.data.servedQuantity").value(3));
    }

    private ResultActions serve(
            long id,
            UsernamePasswordAuthenticationToken staff,
            int quantity,
            int expectedServedQuantity)
            throws Exception {
        return mvc.perform(
                post("/api/v1/order-details/{id}/serve-portions", id)
                        .with(authentication(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"quantity":%d,"expectedServedQuantity":%d}
                        """
                                        .formatted(quantity, expectedServedQuantity)));
    }
}
