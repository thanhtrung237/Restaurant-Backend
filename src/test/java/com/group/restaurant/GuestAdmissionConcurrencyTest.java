package com.group.restaurant;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.restaurant.configurations.jwt.JwtUtil;
import com.group.restaurant.enums.CategoryStatus;
import com.group.restaurant.enums.DishStatus;
import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.enums.StockStatus;
import com.group.restaurant.models.*;
import com.group.restaurant.repositories.*;
import com.group.restaurant.services.BranchDishStockService;
import com.group.restaurant.services.guest.GuestAdmissionService;
import java.math.BigDecimal;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GuestAdmissionConcurrencyTest {
    @Autowired RestaurantRepository restaurants;
    @Autowired BranchRepository branches;
    @Autowired DiningTableRepository tables;
    @Autowired TableSessionRepository sessions;
    @Autowired CategoryRepository categories;
    @Autowired DishRepository dishes;
    @Autowired BranchDishStockRepository stocks;
    @Autowired BranchDishStockService stockService;
    @Autowired StaffNotificationRepository notifications;
    @Autowired GuestAdmissionService admission;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired AccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired JwtUtil jwt;
    @Autowired SimpleBrokerMessageHandler broker;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @Test
    void simultaneousRequestsCreateOneSessionAndNotifyStaffOverWebSocket() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        Long[] ids =
                tx.execute(
                        status -> {
                            Restaurant r = new Restaurant();
                            r.setCode(UUID.randomUUID().toString());
                            r.setName("Concurrent");
                            restaurants.save(r);
                            Branch b = new Branch();
                            b.setRestaurant(r);
                            b.setCode("C");
                            b.setName("Concurrent branch");
                            branches.save(b);
                            DiningTable table =
                                    DiningTable.builder()
                                            .branch(b)
                                            .name("Concurrent table")
                                            .qrCode("CONCURRENT")
                                            .build();
                            tables.save(table);
                            return new Long[] {b.getId(), table.getId()};
                        });
        var request = new GuestAdmissionService.AccessRequest(ids[0], "CONCURRENT", 2, null, null);
        String topic = "/topic/branch/" + ids[0] + "/table-open-request";
        var stomp =
                new WebSocketStompClient(
                        new SockJsClient(
                                List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        var headers = new StompHeaders();
        headers.set("Authorization", "Bearer " + staffToken(tx, ids[0]));
        var received = new CompletableFuture<byte[]>();
        StompSession socket = null;
        try {
            socket =
                    stomp.connectAsync(
                                    "http://127.0.0.1:" + port + "/ws",
                                    new WebSocketHttpHeaders(),
                                    headers,
                                    new StompSessionHandlerAdapter() {})
                            .get(10, TimeUnit.SECONDS);
            socket.subscribe(
                    topic,
                    new StompFrameHandler() {
                        @Override
                        public Type getPayloadType(StompHeaders frame) {
                            return byte[].class;
                        }

                        @Override
                        public void handleFrame(StompHeaders frame, Object payload) {
                            received.complete((byte[]) payload);
                        }
                    });
            var push = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            push.setDestination(topic);
            var message = MessageBuilder.createMessage(new byte[0], push.getMessageHeaders());
            await().until(() -> !broker.getSubscriptionRegistry().findSubscriptions(message).isEmpty());

            var gate = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                Callable<Long> attempt =
                        () -> {
                            gate.await();
                            return admission
                                    .enter(request, UUID.randomUUID().toString())
                                    .session()
                                    .getId();
                        };
                var first = pool.submit(attempt);
                var second = pool.submit(attempt);
                gate.countDown();
                Long sessionId = first.get(10, TimeUnit.SECONDS);
                assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(sessionId);
                var event = json.readTree(received.get(5, TimeUnit.SECONDS));
                assertThat(event.path("type").asText()).isEqualTo("TABLE_OPEN_REQUESTED");
                assertThat(event.path("tableNo").asText()).isEqualTo("Concurrent table");
                assertThat(event.path("tableSessionId").asLong()).isEqualTo(sessionId);
                assertThat(event.path("notificationId").asLong()).isPositive();
            }
        } finally {
            if (socket != null && socket.isConnected()) socket.disconnect();
            stomp.stop();
        }
        Integer sessionCount = tx.execute(status -> sessions.findByTableId(ids[1]).size());
        assertThat(sessionCount).isEqualTo(1);
        var inbox = notifications.findInbox(ids[0], 0L, PageRequest.of(0, 50));
        assertThat(inbox)
                .singleElement()
                .satisfies(
                        row -> {
                            var notice = row.getNotification();
                            assertThat(notice.getType())
                                    .isEqualTo(StaffNotification.Type.TABLE_OPEN_REQUESTED);
                            assertThat(notice.getTableName()).isEqualTo("Concurrent table");
                            assertThat(notice.getTableSessionId())
                                    .isEqualTo(sessions.findByTableId(ids[1]).getFirst().getId());
                        });
    }


    @Test
    void guestReceivesStockResetOverSockJsOnlyAfterCommit() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        Long[] ids = tx.execute(status -> {
            var restaurant = new Restaurant();
            restaurant.setCode(UUID.randomUUID().toString());
            restaurant.setName("Stock realtime");
            restaurants.save(restaurant);
            var branch = new Branch();
            branch.setRestaurant(restaurant);
            branch.setCode("S");
            branch.setName("Stock branch");
            branches.save(branch);
            tables.save(DiningTable.builder().branch(branch).name("Stock table").qrCode("STOCK-WS").build());
            var category = categories.save(Category.builder().restaurant(restaurant)
                    .name("Menu").status(CategoryStatus.ACTIVE).build());
            var dish = dishes.save(Dish.builder().restaurant(restaurant).category(category)
                    .name("Stock dish").basePrice(BigDecimal.TEN).soldByWeight(false)
                    .status(DishStatus.ACTIVE).build());
            stocks.save(BranchDishStock.builder().branch(branch).dish(dish).status(StockStatus.OUT_OF_STOCK).build());
            return new Long[] { restaurant.getId(), branch.getId(), dish.getId() };
        });
        var access = admission.enter(
                new GuestAdmissionService.AccessRequest(ids[1], "STOCK-WS", 2, null, null),
                UUID.randomUUID().toString());
        var stomp = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        var connectHeaders = new StompHeaders();
        connectHeaders.set("X-Guest-Token", access.token());
        var frames = new LinkedBlockingQueue<byte[]>();
        StompSession socket = null;
        try {
            socket = stomp.connectAsync("http://127.0.0.1:" + port + "/ws",
                    new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                    .get(10, TimeUnit.SECONDS);
            String topic = "/topic/restaurant/" + ids[0] + "/menu-updated";
            var subscription = new StompHeaders();
            subscription.setDestination(topic);
            subscription.set("X-Guest-Session", access.session().getId().toString());
            socket.subscribe(subscription, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders frame) {
                    return byte[].class;
                }

                @Override
                public void handleFrame(StompHeaders frame, Object payload) {
                    frames.add((byte[]) payload);
                }
            });
            var push = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            push.setDestination(topic);
            var message = MessageBuilder.createMessage(new byte[0], push.getMessageHeaders());
            await().until(() -> !broker.getSubscriptionRegistry().findSubscriptions(message).isEmpty());

            tx.executeWithoutResult(status -> {
                assertThat(stockService.resetStockForBranch(ids[1])).isEqualTo(1);
                status.setRollbackOnly();
            });
            assertThat(frames.poll(350, TimeUnit.MILLISECONDS)).isNull();
            assertThat(stockService.isAvailable(ids[1], ids[2])).isFalse();

            assertThat(stockService.resetStockForBranch(ids[1])).isEqualTo(1);
            byte[] payload = frames.poll(5, TimeUnit.SECONDS);
            assertThat(payload).isNotNull();
            assertThat(json.readTree(payload).path("changed").asBoolean()).isTrue();
            assertThat(stockService.isAvailable(ids[1], ids[2])).isTrue();
        } finally {
            if (socket != null && socket.isConnected()) socket.disconnect();
            stomp.stop();
        }
    }

    private String staffToken(TransactionTemplate tx, Long branchId) {
        return tx.execute(
                status -> {
                    Branch branch = branches.findById(branchId).orElseThrow();
                    Role role =
                            roles.save(
                                    Role.builder()
                                            .restaurant(branch.getRestaurant())
                                            .code("WAITER")
                                            .name("Waiter")
                                            .scope(RoleScope.BRANCH)
                                            .build());
                    Account account =
                            Account.builder()
                                    .restaurant(branch.getRestaurant())
                                    .username("waiter")
                                    .fullName("Test waiter")
                                    .employeeCode("WS-TEST")
                                    .passwordHash("unused")
                                    .build();
                    account.getAssignments()
                            .add(Assignment.builder().account(account).role(role).branch(branch).build());
                    return jwt.generateAccessToken(accounts.save(account));
                });
    }
}
