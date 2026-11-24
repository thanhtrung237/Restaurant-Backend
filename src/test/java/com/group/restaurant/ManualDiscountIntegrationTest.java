package com.group.restaurant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.enums.*;
import com.group.restaurant.models.*;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ManualDiscountIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager entities;

    @Test
    void replacesPriorInvoiceDiscountAndRecalculatesFeesWithoutAccumulatingIt() throws Exception {
        var restaurant = new Restaurant();
        restaurant.setCode("DISCOUNT-TEST");
        restaurant.setName("Discount test");
        entities.persist(restaurant);
        var branch = new Branch();
        branch.setRestaurant(restaurant);
        branch.setCode("DISC");
        branch.setName("Discount test branch");
        entities.persist(branch);
        persistPromotion(restaurant, "Ưu tiên cao", 100, 2_000);
        var best = persistPromotion(restaurant, "Giảm nhiều nhất", 1, 5_000);
        var table = DiningTable.builder().branch(branch).name("Bàn 1").build();
        entities.persist(table);
        var session =
                TableSession.builder()
                        .table(table)
                        .branch(branch)
                        .status(TableSessionStatus.WAITING_FOR_PAY)
                        .serviceFeeRateAtOpen(BigDecimal.valueOf(5))
                        .vatRateAtOpen(BigDecimal.ZERO)
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
        var price = BigDecimal.valueOf(50_000);
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
        entities.persist(
                OrderDetail.builder()
                        .order(order)
                        .tableSession(session)
                        .dish(dish)
                        .quantity(1)
                        .priceAtOrderTime(price)
                        .price(price)
                        .status(OrderDetailStatus.SERVED)
                        .build());

        var staff = staff(restaurant, branch, "CASHIER", 903L);
        var manager = staff(restaurant, branch, "BRANCH_MANAGER", 904L);

        mvc.perform(
                        post("/api/v1/invoices")
                                .with(authentication(staff))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"branchId":%d,"tableSessionId":%d}
                                """
                                                .formatted(branch.getId(), session.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.promotionId").value(best.getId()))
                .andExpect(jsonPath("$.data.grandTotal").value(47_250));
        Long invoiceId =
                entities.createQuery(
                                "select i.id from Invoice i where i.tableSession.id = :sessionId",
                                Long.class)
                        .setParameter("sessionId", session.getId())
                        .getSingleResult();

        putDiscount(staff, invoiceId, 15_000, 31_500);
        putDiscount(staff, invoiceId, 10_000, 36_750);
        putDiscount(staff, invoiceId, 10_000, 36_750);
        entities.flush();
        entities.clear();

        mvc.perform(
                        get("/api/v1/invoices/session/{id}", session.getId())
                                .with(authentication(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalManualDiscount").value(10_000))
                .andExpect(jsonPath("$.data.grandTotal").value(36_750));
        mvc.perform(
                        delete("/api/v1/manual-discounts/invoice/{id}", invoiceId)
                                .with(authentication(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalManualDiscount").value(0))
                .andExpect(jsonPath("$.data.grandTotal").value(47_250));

        mvc.perform(
                        post("/api/v1/invoices/{id}/share-quote", invoiceId)
                                .with(authentication(staff))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"expectedTotal\":47250}"))
                .andExpect(status().isNoContent());
        discountRequest(staff, invoiceId, 10_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discountApprovalPending").value(true))
                .andExpect(jsonPath("$.data.totalManualDiscount").value(0))
                .andExpect(jsonPath("$.data.grandTotal").value(47_250));
        var approvalResult =
                mvc.perform(
                                get("/api/v1/manual-discounts/invoice/{id}/approval", invoiceId)
                                        .with(authentication(manager)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.value").value(10_000))
                        .andReturn();
        String requestId =
                JsonPath.read(
                        approvalResult.getResponse().getContentAsString(), "$.data.requestId");
        var approval = "{\"requestId\":\"%s\"}".formatted(requestId);
        mvc.perform(
                        post("/api/v1/manual-discounts/invoice/{id}/approval/true", invoiceId)
                                .with(authentication(manager))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(approval))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discountApprovalPending").value(false))
                .andExpect(jsonPath("$.data.totalManualDiscount").value(10_000))
                .andExpect(jsonPath("$.data.grandTotal").value(36_750));
        mvc.perform(
                        post("/api/v1/invoices")
                                .with(authentication(staff))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"branchId":%d,"tableSessionId":%d,"promotionId":-1}
                                """
                                                .formatted(branch.getId(), session.getId())))
                .andExpect(status().isBadRequest());
    }

    private Promotion persistPromotion(
            Restaurant restaurant, String name, int priority, int amount) {
        var promotion =
                Promotion.builder()
                        .restaurant(restaurant)
                        .name(name)
                        .applyLevel(ApplyLevel.INVOICE)
                        .status(PromotionStatus.ACTIVE)
                        .startDate(LocalDate.of(2020, 1, 1))
                        .endDate(LocalDate.of(2090, 1, 1))
                        .allBranches(true)
                        .priority(priority)
                        .condition("{\"target\":\"all_order\"}")
                        .action("{\"type\":\"fixed_amount\",\"value\":%d}".formatted(amount))
                        .build();
        entities.persist(promotion);
        return promotion;
    }

    private UsernamePasswordAuthenticationToken staff(
            Restaurant restaurant, Branch branch, String code, Long id) {
        var role = Role.builder().code(code).scope(RoleScope.BRANCH).build();
        var account =
                Account.builder()
                        .id(id)
                        .username("discount-test-" + id)
                        .restaurant(restaurant)
                        .build();
        account.setAssignments(
                List.of(Assignment.builder().account(account).branch(branch).role(role).build()));
        var principal = new AccountUserDetails(account);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private void putDiscount(
            UsernamePasswordAuthenticationToken staff, Long invoiceId, int amount, int total)
            throws Exception {
        discountRequest(staff, invoiceId, amount)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalManualDiscount").value(amount))
                .andExpect(jsonPath("$.data.grandTotal").value(total));
    }

    private org.springframework.test.web.servlet.ResultActions discountRequest(
            UsernamePasswordAuthenticationToken staff, Long invoiceId, int amount)
            throws Exception {
        return mvc.perform(
                put("/api/v1/manual-discounts/invoice/{id}", invoiceId)
                        .with(authentication(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"type":"FIXED_AMOUNT","value":%d,"reason":"Điều chỉnh chiết khấu"}
                                """
                                        .formatted(amount)));
    }
}
