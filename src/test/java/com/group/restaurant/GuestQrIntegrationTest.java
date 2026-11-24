package com.group.restaurant;

import com.fasterxml.jackson.databind.*;
import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.enums.*;
import com.group.restaurant.models.*;
import com.group.restaurant.repositories.*;
import com.group.restaurant.services.guest.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@SpringBootTest(properties = {"SEPAY_WEBHOOK_SECRET=integration-test-key", "SEPAY_BANK=MB", "SEPAY_ACCOUNT_NUMBER=123456789"})
@AutoConfigureMockMvc
@Transactional
class GuestQrIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    @Autowired RestaurantRepository restaurants;
    @Autowired BranchRepository branches;
    @Autowired DiningTableRepository tables;
    @Autowired TableSessionRepository sessions;
    @Autowired CategoryRepository categories;
    @Autowired DishRepository dishes;
    @Autowired OrderRepository orders;
    @Autowired AccountRepository accounts;
    @Autowired OptionGroupRepository optionGroups;
    @Autowired OptionItemRepository optionItems;
    @Autowired DishOptionGroupRepository dishOptionGroups;
    @Autowired RoleRepository roles;
    @Autowired OrderDetailRepository orderDetails;
    @Autowired SepayDiscordNoticeRepository discordNotices;
    @org.springframework.boot.test.mock.mockito.MockBean com.group.restaurant.services.DiscordTransactionSender discord;
    @Autowired GuestTokenService tokens;
    @Autowired com.group.restaurant.services.OrderNotificationService notifications;
    @Autowired com.group.restaurant.configurations.GuestWebSocketInterceptor websocket;
    Branch branch;
    DiningTable table;
    String ip;
    @BeforeEach void setup() {
        ip = UUID.randomUUID().toString();
        Restaurant restaurant = new Restaurant(); restaurant.setCode(UUID.randomUUID().toString()); restaurant.setName("Test restaurant"); restaurants.save(restaurant);
        branch = new Branch(); branch.setRestaurant(restaurant); branch.setCode("TEST"); branch.setName("Test branch");
        branch.setLatitude(10d); branch.setLongitude(106d); branch.setGeofenceEnabled(true); branches.save(branch);
        table = DiningTable.builder().branch(branch).name("Table 1").qrCode("QR-B"+branch.getId()+"-T1").build(); tables.save(table);
    }
    private Map<String,Object> body(boolean location) {
        Map<String,Object> body = new HashMap<>(); body.put("branchId",branch.getId()); body.put("qrCode",table.getQrCode()); body.put("guestCount",2);
        if (location) body.put("location", Map.of("latitude",10,"longitude",106,"accuracy",10,"timestamp",System.currentTimeMillis()));
        return body;
    }
    private ResultActions enter(Map<String,Object> body) throws Exception {
        return mvc.perform(post("/api/v1/guest/access").with(r -> { r.setRemoteAddr(ip); return r; }).contentType("application/json").content(json.writeValueAsString(body)));
    }
    private JsonNode createGuest() throws Exception {
        return json.readTree(enter(body(true)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    @Test void signedSepayTransferRequiresExactAmountAndOnlyRecordsOnce() throws Exception {
        org.mockito.Mockito.when(discord.enabled()).thenReturn(true);
        branch.setBankName("MB Bank");
        branch.setBankAccountNo("123456789");
        branches.save(branch);
        var staff = notificationStaff(branch, "sepay-test");
        var category = categories.save(Category.builder().restaurant(branch.getRestaurant()).name("Food").status(CategoryStatus.ACTIVE).build());
        var dish = dishes.save(Dish.builder().restaurant(branch.getRestaurant()).category(category).name("Soup")
                .basePrice(new BigDecimal("20000")).soldByWeight(false).status(DishStatus.ACTIVE).build());
        var session = sessions.save(TableSession.builder().table(table).branch(branch).status(TableSessionStatus.SERVING)
                .vatRateAtOpen(new BigDecimal("8")).serviceFeeRateAtOpen(new BigDecimal("5")).build());
        long sessionId = session.getId();
        mvc.perform(post("/api/v1/orders").with(authentication(staff)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("tableSessionId", sessionId,
                                "items", List.of(Map.of("dishId", dish.getId(), "quantity", 1),
                                        Map.of("dishId", dish.getId(), "quantity", 1))))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/table-sessions/{id}/request-payment", sessionId).with(authentication(staff)))
                .andExpect(status().isOk());
        String response = mvc.perform(post("/api/v1/invoices").with(authentication(staff)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("tableSessionId", sessionId, "branchId", branch.getId()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var invoice = json.readTree(response).path("data");
        long invoiceId = invoice.path("id").asLong();
        BigDecimal originalAmount = invoice.path("grandTotal").decimalValue();
        String code = "PV" + invoiceId + "X";
        mvc.perform(get("/api/v1/invoices/{id}/qr-code", invoiceId).with(authentication(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.transferContent").value(code))
                .andExpect(jsonPath("$.data.automaticVerification").value(true));
        String qrOpenedPath = "/api/v1/invoices/session/" + sessionId + "/qr-opened";
        mvc.perform(post(qrOpenedPath)).andExpect(status().isForbidden());
        String token = tokens.issue(session);
        mvc.perform(post(qrOpenedPath).header("X-Guest-Token", token)).andExpect(status().isNoContent());
        mvc.perform(post(qrOpenedPath).header("X-Guest-Token", token)).andExpect(status().isNoContent());
        String inbox = "/api/v1/staff-notifications/branch/" + branch.getId();
        mvc.perform(get(inbox).with(authentication(staff)))
                .andExpect(jsonPath("$.data[?(@.type == 'PAYMENT_QR_OPENED')]", org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(get("/api/v1/invoices/{id}", invoiceId).with(authentication(staff)))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        var details = orderDetails.findByTableSessionId(sessionId);
        long removedId = details.getFirst().getId();
        mvc.perform(post("/api/v1/order-details/batch-confirm").with(authentication(staff)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("orderDetailIds", List.of(removedId)))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/order-details/session/{sessionId}/remove/{id}", sessionId, removedId)
                        .with(authentication(staff)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("reason", "Restaurant removed the duplicate item"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isBillable").value(false));
        String updatedQr = mvc.perform(get("/api/v1/invoices/{id}/qr-code", invoiceId).with(authentication(staff)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        BigDecimal amount = json.readTree(updatedQr).path("data").path("remainingAmount").decimalValue();
        assertThat(amount).isPositive().isLessThan(originalAmount);
        sendSepay(100L, code, "123456789", originalAmount, true).andExpect(status().isOk());

        sendSepay(101L, code, "123456789", amount, false).andExpect(status().isUnauthorized());
        sendSepay(102L, code, "000000000", amount, true).andExpect(status().isOk());
        sendSepay(103L, code, "123456789", amount.subtract(BigDecimal.ONE), true).andExpect(status().isOk());
        sendSepay(104L, code + " PV999X", "123456789", amount, true).andExpect(status().isOk());
        sendSepay(106L, code, "123456789", amount, true, "Sacombank").andExpect(status().isOk());
        sendSepay(107L, "ban 5", "123456789", amount, true).andExpect(status().isOk());
        sendSepay(107L, "ban 5", "123456789", amount, true).andExpect(status().isOk());
        mvc.perform(get("/api/v1/invoices/{id}", invoiceId).with(authentication(staff)))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        sendSepay(105L, code, "123456789", amount, true)
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        sendSepay(105L, code, "123456789", amount, true).andExpect(status().isOk());
        assertThat(discordNotices.findAll()).extracting(SepayDiscordNotice::getTransactionId)
                .containsExactlyInAnyOrder(100L, 103L, 104L, 105L, 107L);
        assertThat(discordNotices.findById(107L).orElseThrow().getMessage()).contains("Chờ đối chiếu");
        assertThat(discordNotices.findById(105L).orElseThrow().getMessage()).contains(code, "Đã thanh toán");
        mvc.perform(get(inbox).with(authentication(staff)))
                .andExpect(jsonPath("$.data[?(@.type == 'BANK_PAYMENT_VERIFIED')]", org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(post("/api/v1/order-details/session/{sessionId}/remove/{id}", sessionId, details.getLast().getId())
                        .with(authentication(staff))).andExpect(status().isConflict());
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get("/api/v1/invoices/{id}", invoiceId).with(authentication(staff)))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.payments.length()").value(1))
                .andExpect(jsonPath("$.data.payments[0].bankVerified").value(true));
    }

    private ResultActions sendSepay(long id, String code, String account, BigDecimal amount, boolean authentic) throws Exception {
        return sendSepay(id, code, account, amount, authentic, "MBBank");
    }

    private ResultActions sendSepay(long id, String code, String account, BigDecimal amount, boolean authentic, String gateway) throws Exception {
        byte[] body = json.writeValueAsBytes(Map.of("id", id, "gateway", gateway, "accountNumber", account,
                "transferType", "in", "content", "TT " + code, "transferAmount", amount, "referenceCode", "REFERENCE"));
        String timestamp = Long.toString(java.time.Instant.now().getEpochSecond());
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                (authentic ? "integration-test-key" : "wrong-key").getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        return mvc.perform(post("/api/v1/webhooks/sepay").contentType("application/json").content(body)
                .header("X-SePay-Timestamp", timestamp).header("X-SePay-Signature", signature));
    }
    @Test void notificationHistoryIsBranchScopedWithPersonalReceiptsAndSharedResolution() throws Exception {
        var session = sessions.save(TableSession.builder().table(table).branch(branch)
                .status(TableSessionStatus.SERVING).build());
        notifications.notifyNewOrder(branch.getId(), 105L, session.getId(), table.getName(), null);
        notifications.notifyStaffCall(branch.getId(), session.getId(), null, table.getName(), "");
        var first = notificationStaff(branch, "first");
        var second = notificationStaff(branch, "second");
        var other = new Branch();
        other.setRestaurant(branch.getRestaurant()); other.setCode("OTHER"); other.setName("Other");
        branches.save(other);
        var outsider = notificationStaff(other, "outsider");
        String path = "/api/v1/staff-notifications/branch/" + branch.getId();

        mvc.perform(get(path)).andExpect(status().is4xxClientError());
        mvc.perform(get(path).with(authentication(outsider))).andExpect(status().isForbidden());
        String response = mvc.perform(get(path).with(authentication(first))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].type").value("STAFF_CALL"))
                .andExpect(jsonPath("$.data[1].orderId").value(105))
                .andReturn().getResponse().getContentAsString();
        long callId = json.readTree(response).path("data").get(0).path("id").asLong();
        long orderId = json.readTree(response).path("data").get(1).path("id").asLong();

        mvc.perform(put(path + "/" + orderId + "/read").with(authentication(first))).andExpect(status().isNoContent());
        mvc.perform(get(path).with(authentication(first))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].read").value(true));
        mvc.perform(get(path).with(authentication(second))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].read").value(false));
        mvc.perform(put(path + "/" + orderId + "/resolve").with(authentication(first))).andExpect(status().isConflict());
        mvc.perform(put(path + "/" + callId + "/resolve").with(authentication(first))).andExpect(status().isNoContent());
        mvc.perform(put(path + "/" + callId + "/resolve").with(authentication(second))).andExpect(status().isNoContent());
        mvc.perform(get(path).with(authentication(second))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].resolvedAt").exists());

        mvc.perform(put(path + "/read").with(authentication(first))).andExpect(status().isNoContent());
        mvc.perform(delete(path).with(authentication(first))).andExpect(status().isNoContent());
        mvc.perform(get(path).with(authentication(first))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get(path).with(authentication(second))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    private UsernamePasswordAuthenticationToken notificationStaff(Branch assigned, String name) {
        Account account = accounts.save(Account.builder().restaurant(assigned.getRestaurant()).fullName(name)
                .employeeCode(name).username("inbox-" + name).passwordHash("unused").build());
        var login = Account.builder().id(account.getId()).username(account.getUsername())
                .restaurant(assigned.getRestaurant()).build();
        login.setAssignments(List.of(Assignment.builder().account(login).branch(assigned)
                .role(Role.builder().code("BRANCH_MANAGER").scope(RoleScope.BRANCH).build()).build()));
        var principal = new AccountUserDetails(login);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test void takeawayCanBePaidBeforeKitchenFinishesAndHandsOffWithoutOccupyingATable() throws Exception {
        var staff = notificationStaff(branch, "takeaway-test");
        var category = categories.save(Category.builder().restaurant(branch.getRestaurant()).name("Food").status(CategoryStatus.ACTIVE).build());
        var dish = dishes.save(Dish.builder().restaurant(branch.getRestaurant()).category(category).name("Soup")
                .basePrice(new BigDecimal("45000")).soldByWeight(false).status(DishStatus.ACTIVE).build());
        String path = "/api/v1/takeaway-orders/branch/" + branch.getId();
        String created = mvc.perform(post(path).with(authentication(staff)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("customerName", "Khách quầy",
                                "items", List.of(Map.of("dishId", dish.getId(), "quantity", 2))))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.kitchenStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.portions").value(2)).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).path("data").path("id").asLong();
        var session = sessions.findById(id).orElseThrow();
        assertThat(session.getTable()).isNull();
        assertThat(tables.findById(table.getId()).orElseThrow().getStatus()).isEqualTo(DiningTableStatus.AVAILABLE);
        var item = orderDetails.findByTableSessionId(id).getFirst();
        assertThat(item.getIsTakeAway()).isTrue();
        mvc.perform(post("/api/v1/table-sessions/{id}/request-payment", id).with(authentication(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_FOR_PAY"))
                .andExpect(jsonPath("$.data.canAddTakeawayItems").value(true));
        mvc.perform(get(path).with(authentication(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].canAddTakeawayItems").value(true));
        String extraItems = json.writeValueAsString(Map.of("tableSessionId", id,
                "items", List.of(Map.of("dishId", dish.getId(), "quantity", 1))));
        mvc.perform(post("/api/v1/orders").with(authentication(staff)).contentType("application/json").content(extraItems))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.tableSessionId").value(id));
        var extraItem = orderDetails.findByTableSessionId(id).stream()
                .filter(detail -> !detail.getId().equals(item.getId())).findFirst().orElseThrow();
        assertThat(extraItem.getIsTakeAway()).isTrue();
        assertThat(extraItem.getOrder().getId()).isNotEqualTo(item.getOrder().getId());
        mvc.perform(get(path).with(authentication(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].portions").value(3))
                .andExpect(jsonPath("$.data[0].sessionStatus").value("SERVING"));
        String handoff = path + "/" + id + "/handoff";
        mvc.perform(put(handoff).with(authentication(staff))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/table-sessions/{id}/request-payment", id).with(authentication(staff)))
                .andExpect(status().isOk());
        String generated = mvc.perform(post("/api/v1/invoices").with(authentication(staff)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("tableSessionId", id, "branchId", branch.getId()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.takeaway").value(true))
                .andExpect(jsonPath("$.data.subTotalBeforeDiscount").value(135000))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/table-sessions/{id}", id).with(authentication(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canAddTakeawayItems").value(false));
        mvc.perform(get(path).with(authentication(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].canAddTakeawayItems").value(false));
        mvc.perform(post("/api/v1/orders").with(authentication(staff)).contentType("application/json").content(extraItems))
                .andExpect(status().isConflict());
        JsonNode invoice = json.readTree(generated).path("data");
        mvc.perform(post("/api/v1/payments").with(authentication(staff)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("invoiceId", invoice.path("id").asLong(), "method", "CASH",
                                "amount", invoice.path("grandTotal").decimalValue())))).andExpect(status().isCreated());
        session.setStatus(TableSessionStatus.CLOSED); // Hóa đơn điện tử có thể đã phát hành trước khi bếp làm xong.
        mvc.perform(get("/api/v1/table-sessions/branch/{id}/open", branch.getId()).with(authentication(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id));
        mvc.perform(put(handoff).with(authentication(staff))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/order-details/batch-confirm").with(authentication(staff)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("orderDetailIds", List.of(item.getId(), extraItem.getId())))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/order-details/{id}/serve-portions", item.getId()).with(authentication(staff))
                        .contentType("application/json").content("{\"expectedServedQuantity\":0,\"quantity\":2}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/order-details/{id}/serve-portions", extraItem.getId()).with(authentication(staff))
                        .contentType("application/json").content("{\"expectedServedQuantity\":0,\"quantity\":1}"))
                .andExpect(status().isOk());
        mvc.perform(get(path).with(authentication(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].kitchenStatus").value("READY"))
                .andExpect(jsonPath("$.data[0].paid").value(true));
        mvc.perform(put(handoff).with(authentication(staff))).andExpect(status().isNoContent());
        mvc.perform(put(handoff).with(authentication(staff))).andExpect(status().isNoContent());
        mvc.perform(get(path).with(authentication(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
    @Test void publicMetadataExposesNoSessionOrJoinCode() throws Exception {
        mvc.perform(get("/api/v1/guest/table").param("branchId",branch.getId().toString()).param("qrCode",table.getQrCode()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.locationRequired").value(true))
                .andExpect(jsonPath("$.data.token").doesNotExist()).andExpect(jsonPath("$.data.guestJoinCode").doesNotExist());
    }
    @Test void missingAndFarLocationDoNotCreateSessions() throws Exception {
        enter(body(false)).andExpect(status().isForbidden());
        var far=body(true); far.put("location",Map.of("latitude",11,"longitude",106,"accuracy",10,"timestamp",System.currentTimeMillis()));
        enter(far).andExpect(status().isForbidden());
        assertThat(sessions.findByTableId(table.getId())).isEmpty();
        assertThat(tables.findById(table.getId()).orElseThrow().getStatus()).isEqualTo(DiningTableStatus.AVAILABLE);
    }
    @Test void openPollAndOrderRequireServerToken() throws Exception {
        JsonNode access=createGuest(); long id=access.path("session").path("id").asLong(); String token=access.path("token").asText();
        mvc.perform(get("/api/v1/table-sessions/"+id)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/table-sessions/"+id).header("X-Guest-Token","session-"+id)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/table-sessions/"+id).header("X-Guest-Token",token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WAITING_FOR_OPEN"));
        mvc.perform(get("/api/v1/table-sessions/table/"+table.getId()+"/open")).andExpect(status().isForbidden());
        var order=Map.of("tableSessionId",id,"items",List.of(Map.of("dishId",1,"quantity",1)));
        mvc.perform(post("/api/v1/orders").contentType("application/json").content(json.writeValueAsString(order))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders").header("X-Guest-Token",token).contentType("application/json").content(json.writeValueAsString(order))).andExpect(status().isConflict());
    }
    @Test void sessionTokenCannotReadAnotherSession() throws Exception {
        JsonNode access=createGuest();
        TableSession other=TableSession.builder().table(table).branch(branch).status(TableSessionStatus.SERVING).build(); sessions.save(other);
        String token=access.path("token").asText();
        for(String endpoint:List.of("/table-sessions/"+other.getId(),"/orders/session/"+other.getId(),"/order-details/session/"+other.getId(),"/table-sessions/"+other.getId()+"/bill-preview","/invoices/session/"+other.getId())) {
            mvc.perform(get("/api/v1"+endpoint).header("X-Guest-Token",token)).andExpect(status().isForbidden());
        }
    }
    @Test void existingSessionAcceptsValidLocationWithoutCode() throws Exception {
        JsonNode access=createGuest(); long id=access.path("session").path("id").asLong();
        for (var state : List.of(TableSessionStatus.WAITING_FOR_OPEN, TableSessionStatus.SERVING, TableSessionStatus.WAITING_FOR_PAY)) {
            sessions.findById(id).orElseThrow().setStatus(state);
            enter(body(true)).andExpect(status().isOk()).andExpect(jsonPath("$.data.session.id").value(id))
                    .andExpect(jsonPath("$.data.session.status").value(state.name()))
                    .andExpect(jsonPath("$.data.token").isNotEmpty());
        }
        assertThat(sessions.findByTableId(table.getId())).hasSize(1);
    }
    @Test void existingSessionStillRejectsFarLocationWithoutCode() throws Exception {
        createGuest(); var request=body(true);
        request.put("location",Map.of("latitude",11,"longitude",106,"accuracy",10,"timestamp",System.currentTimeMillis()));
        enter(request).andExpect(status().isForbidden());
        assertThat(sessions.findByTableId(table.getId())).hasSize(1);
    }
    @Test void disabledGeofenceAllowsExistingSessionWithoutCodeOrLocation() throws Exception {
        JsonNode access=createGuest(); long id=access.path("session").path("id").asLong();
        branch.setGeofenceEnabled(false);
        enter(body(false)).andExpect(status().isOk()).andExpect(jsonPath("$.data.session.id").value(id));
        assertThat(sessions.findByTableId(table.getId())).hasSize(1);
    }
    @Test void staffCodeAllowsAssistedJoinButWrongCodeDoesNot() throws Exception {
        JsonNode access=createGuest(); long id=access.path("session").path("id").asLong();
        var user=staff(branch,"WAITER",RoleScope.BRANCH);
        String code=json.readTree(mvc.perform(post("/api/v1/table-sessions/"+id+"/guest-code").with(authentication(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").asText();
        assertThat(code).matches("[0-9]{8}");
        enter(body(false)).andExpect(status().isForbidden());
        var wrong=body(false); wrong.put("joinCode",code.equals("00000000") ? "11111111" : "00000000"); enter(wrong).andExpect(status().isForbidden());
        var join=body(false); join.put("joinCode",code);
        enter(join).andExpect(status().isOk()).andExpect(jsonPath("$.data.session.id").value(id));
        assertThat(sessions.findByTableId(table.getId())).hasSize(1);
    }
    @Test void directOldCreateAndScanCannotBypassAdmission() throws Exception {
        mvc.perform(post("/api/v1/table-sessions").contentType("application/json").content("{\"tableId\":"+table.getId()+"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tables/scan").param("tableId",table.getId().toString())).andExpect(status().isForbidden());
    }
    @Test void repeatedAdmissionsAreLimited() throws Exception {
        var body=body(false);
        for(int i=0;i<5;i++) enter(body).andExpect(status().isForbidden());
        enter(body).andExpect(status().isTooManyRequests());
    }
    @Test void closedSessionCanReadReceiptButCannotMutate() throws Exception {
        JsonNode access=createGuest(); Long id=access.path("session").path("id").asLong(); String token=access.path("token").asText();
        sessions.findById(id).orElseThrow().setStatus(TableSessionStatus.CLOSED);
        mvc.perform(get("/api/v1/table-sessions/"+id).header("X-Guest-Token",token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/table-sessions/"+id+"/call-staff").header("X-Guest-Token",token)).andExpect(status().isForbidden());
    }
    @Test void servingGuestCanCreateRealOrderAndCannotSpoofStaff() throws Exception {
        JsonNode access=createGuest(); Long id=access.path("session").path("id").asLong(); String token=access.path("token").asText();
        sessions.findById(id).orElseThrow().setStatus(TableSessionStatus.SERVING);
        Category category=Category.builder().restaurant(branch.getRestaurant()).name("Food").status(CategoryStatus.ACTIVE).build(); categories.save(category);
        Dish dish=Dish.builder().restaurant(branch.getRestaurant()).category(category).name("Soup").basePrice(new BigDecimal("50000")).soldByWeight(false).status(DishStatus.ACTIVE).build(); dishes.save(dish);
        var order=Map.of("tableSessionId",id,"orderedById",999999,"items",List.of(Map.of("dishId",dish.getId(),"quantity",1)));
        mvc.perform(post("/api/v1/orders").header("X-Guest-Token",token).contentType("application/json").content(json.writeValueAsString(order)))
                .andExpect(status().isCreated());
        assertThat(orders.findByTableSessionId(id)).hasSize(1).allMatch(o -> o.getOrderedBy()==null);
    }
    @Test void staffOfOtherBranchCannotGetCode() throws Exception {
        JsonNode access=createGuest(); long id=access.path("session").path("id").asLong();
        Branch other=new Branch(); other.setRestaurant(branch.getRestaurant()); other.setCode("OTHER"); other.setName("Other"); branches.save(other);
        mvc.perform(post("/api/v1/table-sessions/"+id+"/guest-code").with(authentication(staff(other,"WAITER",RoleScope.BRANCH)))).andExpect(status().isForbidden());
    }

    @Test void staffOrderPersistsOptionsAndChargesTheirPricePerServing() throws Exception {
        var session = sessions.save(TableSession.builder().table(table).branch(branch)
                .status(TableSessionStatus.SERVING).build());
        var restaurant = branch.getRestaurant();
        var category = categories.save(Category.builder().restaurant(restaurant).name("Food").status(CategoryStatus.ACTIVE).build());
        var dish = dishes.save(Dish.builder().restaurant(restaurant).category(category).name("Soup")
                .basePrice(new BigDecimal("45000")).soldByWeight(false).status(DishStatus.ACTIVE).build());
        var group = optionGroups.save(OptionGroup.builder().restaurant(restaurant).name("Topping")
                .selectionType(SelectionType.OPTIONAL).maxSelect(2).build());
        var option = optionItems.save(OptionItem.builder().optionGroup(group).name("Egg").extraPrice(new BigDecimal("5000")).build());
        dishOptionGroups.save(DishOptionGroup.builder().dish(dish).optionGroup(group).build());
        var account = accounts.save(Account.builder().restaurant(restaurant).fullName("Staff").employeeCode("OPT-STAFF")
                .username("option-staff").passwordHash("test-only").build());
        var role = Role.builder().code("BRANCH_MANAGER").scope(RoleScope.BRANCH).build();
        var login = Account.builder().id(account.getId()).username(account.getUsername()).restaurant(restaurant).build();
        login.setAssignments(List.of(Assignment.builder().account(login).branch(branch).role(role).build()));
        var principal = new AccountUserDetails(login);
        var staff = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var order = Map.of("tableSessionId", session.getId(), "items", List.of(
                Map.of("dishId", dish.getId(), "quantity", 2, "optionItemIds", List.of(option.getId()))));

        mvc.perform(post("/api/v1/orders").with(authentication(staff)).contentType("application/json")
                        .content(json.writeValueAsString(order))).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/order-details/session/{id}", session.getId()).with(authentication(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].price").value(100000))
                .andExpect(jsonPath("$.data[0].options[0].optionItemName").value("Egg"))
                .andExpect(jsonPath("$.data[0].options[0].priceSnapshot").value(5000));
    }
    @Test void branchLocationConfigIsPersistedAndReturned() throws Exception {
        mvc.perform(put("/api/v1/branches/"+branch.getId()+"/config/admin")
                .with(authentication(staff(branch,"MANAGER",RoleScope.RESTAURANT)))
                .contentType("application/json").content("{\"latitude\":10.5,\"longitude\":106.5,\"geofenceEnabled\":true,\"geofenceRadiusMeters\":200,\"geofenceMaxAccuracyMeters\":50}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.latitude").value(10.5))
                .andExpect(jsonPath("$.data.geofenceRadiusMeters").value(200));
        assertThat(branches.findById(branch.getId()).orElseThrow().getGeofenceMaxAccuracyMeters()).isEqualTo(50);
    }

    @Test void onlyRestaurantAdminCanUpdateOrClearBranchBankDetails() throws Exception {
        String path = "/api/v1/branches/" + branch.getId() + "/config/manager";
        var admin = staff(branch, "MANAGER", RoleScope.RESTAURANT);
        var manager = staff(branch, "BRANCH_MANAGER", RoleScope.BRANCH);
        var cashier = staff(branch, "CASHIER", RoleScope.BRANCH);
        String bank = "{\"bankName\":\"VCB\",\"bankAccountNo\":\"0123456789\"}";

        mvc.perform(put(path).with(authentication(admin)).contentType("application/json").content(bank))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bankAccountNo").value("0123456789"));
        mvc.perform(put(path).with(authentication(manager)).contentType("application/json").content("{\"bankName\":null}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).with(authentication(manager)).contentType("application/json").content("{\"bankAccountNo\":\"111\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).with(authentication(cashier)).contentType("application/json").content(bank))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).with(authentication(manager)).contentType("application/json").content("{\"reservationHoldMinutes\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservationHoldMinutes").value(20))
                .andExpect(jsonPath("$.data.bankName").value("VCB"))
                .andExpect(jsonPath("$.data.bankAccountNo").value("0123456789"));
        mvc.perform(put(path).with(authentication(admin)).contentType("application/json")
                        .content("{\"bankName\":null,\"bankAccountNo\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankName").doesNotExist())
                .andExpect(jsonPath("$.data.bankAccountNo").doesNotExist());
    }

    @Test void invalidCoordinateIsRejectedByRequestValidation() throws Exception {
        var request=body(true); request.put("location",Map.of("latitude",91,"longitude",106,"accuracy",10,"timestamp",System.currentTimeMillis()));
        enter(request).andExpect(status().isBadRequest());
        assertThat(sessions.findByTableId(table.getId())).isEmpty();
    }
    @Test void inactiveBranchCannotAdmitGuests() throws Exception {
        branch.setStatus(BranchStatus.INACTIVE);
        enter(body(true)).andExpect(status().isForbidden());
        assertThat(sessions.findByTableId(table.getId())).isEmpty();
    }
    @Test void accountWithoutRestaurantIsNotAutomaticallyPlatformAdmin() throws Exception {
        Account account=Account.builder().id(998L).username("unassigned").build();
        var details=new AccountUserDetails(account);
        assertThat(details.getAuthorities()).isEmpty();
        var auth=new UsernamePasswordAuthenticationToken(details,null,details.getAuthorities());
        mvc.perform(post("/api/v1/table-sessions/1/guest-code").with(authentication(auth))).andExpect(status().isForbidden());
    }
    @Test void customerHeaderIsAllowedByCors() throws Exception {
        mvc.perform(options("/api/v1/guest/access").header("Origin","http://localhost:5173")
                .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","Content-Type,X-Guest-Token"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
    }
    @Test void staffCreationKeepsOnlyTheExplicitCashierAssignment() throws Exception {
        var manager = staff(branch, "MANAGER", RoleScope.RESTAURANT);
        var account = createRestaurantAccount(manager, "new-cashier")
                .andExpect(jsonPath("$.data.assignments").isEmpty())
                .andReturn().getResponse().getContentAsString();
        long accountId = json.readTree(account).path("data").path("id").asLong();
        var readAssignments = get("/api/v1/assignments/account/{id}", accountId).with(authentication(manager));
        mvc.perform(readAssignments).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());

        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("restaurantCode", branch.getRestaurant().getCode(),
                                "username", "new-cashier", "password", "test-password"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.roles").isEmpty());

        Role cashier = roles.save(Role.builder().restaurant(branch.getRestaurant()).name("Cashier")
                .code("CASHIER").scope(RoleScope.BRANCH).build());
        mvc.perform(post("/api/v1/assignments").with(authentication(manager)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("accountId", accountId,
                                "roleId", cashier.getId(), "branchId", branch.getId()))))
                .andExpect(status().isCreated());
        mvc.perform(readAssignments).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].roleCode").value("CASHIER"))
                .andExpect(jsonPath("$.data[0].branchId").value(branch.getId()));
    }

    @Test void platformOnboardingStillAssignsRestaurantManager() throws Exception {
        Account platform = Account.builder().id(999L).username("test-platform").build();
        Role platformRole = Role.builder().code("PLATFORM_ADMIN").scope(RoleScope.PLATFORM).build();
        platform.setAssignments(List.of(Assignment.builder().account(platform).role(platformRole).build()));
        var principal = new AccountUserDetails(platform);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        createRestaurantAccount(authentication, "new-owner")
                .andExpect(jsonPath("$.data.assignments.length()").value(1))
                .andExpect(jsonPath("$.data.assignments[0].roleCode").value("MANAGER"));
    }

    private ResultActions createRestaurantAccount(UsernamePasswordAuthenticationToken staff, String username) throws Exception {
        return mvc.perform(post("/api/v1/accounts").with(authentication(staff)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("restaurantId", branch.getRestaurant().getId(),
                                "username", username, "fullName", "New employee", "password", "test-password"))))
                .andExpect(status().isCreated());
    }

    private UsernamePasswordAuthenticationToken staff(Branch assigned,String code,RoleScope scope) {
        Role role=Role.builder().code(code).scope(scope).build();
        Account account=Account.builder().id(999L).username("test-staff").restaurant(assigned.getRestaurant()).build();
        account.setAssignments(List.of(Assignment.builder().account(account).branch(assigned).role(role).build()));
        var details=new AccountUserDetails(account);
        return new UsernamePasswordAuthenticationToken(details,null,details.getAuthorities());
    }
}
