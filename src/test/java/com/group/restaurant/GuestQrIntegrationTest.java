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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestQrIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RestaurantRepository restaurants;
    @Autowired BranchRepository branches;
    @Autowired DiningTableRepository tables;
    @Autowired TableSessionRepository sessions;
    @Autowired CategoryRepository categories;
    @Autowired DishRepository dishes;
    @Autowired OrderRepository orders;
    @Autowired GuestTokenService tokens;
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
    @Test void branchLocationConfigIsPersistedAndReturned() throws Exception {
        mvc.perform(put("/api/v1/branches/"+branch.getId()+"/config/admin")
                .with(authentication(staff(branch,"MANAGER",RoleScope.RESTAURANT)))
                .contentType("application/json").content("{\"latitude\":10.5,\"longitude\":106.5,\"geofenceEnabled\":true,\"geofenceRadiusMeters\":200,\"geofenceMaxAccuracyMeters\":50}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.latitude").value(10.5))
                .andExpect(jsonPath("$.data.geofenceRadiusMeters").value(200));
        assertThat(branches.findById(branch.getId()).orElseThrow().getGeofenceMaxAccuracyMeters()).isEqualTo(50);
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
    private UsernamePasswordAuthenticationToken staff(Branch assigned,String code,RoleScope scope) {
        Role role=Role.builder().code(code).scope(scope).build();
        Account account=Account.builder().id(999L).username("test-staff").restaurant(assigned.getRestaurant()).build();
        account.setAssignments(List.of(Assignment.builder().account(account).branch(assigned).role(role).build()));
        var details=new AccountUserDetails(account);
        return new UsernamePasswordAuthenticationToken(details,null,details.getAuthorities());
    }
}
