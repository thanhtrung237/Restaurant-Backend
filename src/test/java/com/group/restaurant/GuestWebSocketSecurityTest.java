package com.group.restaurant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.group.restaurant.configurations.GuestWebSocketInterceptor;
import com.group.restaurant.configurations.jwt.*;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.DiningTable;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.models.TableSession;
import com.group.restaurant.repositories.TableSessionRepository;
import com.group.restaurant.services.guest.*;
import com.group.restaurant.utils.TenantGuard;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;

class GuestWebSocketSecurityTest {
    TableSessionRepository sessions;
    GuestWebSocketInterceptor interceptor;
    Map<String, Object> attributes;
    TableSession session;
    String token;

    @BeforeEach
    void setup() {
        sessions = mock(TableSessionRepository.class);
        var tokens =
                new GuestTokenService(
                        Base64.getEncoder()
                                .encodeToString(
                                        "test-key-at-least-thirty-two-characters-long".getBytes()));
        var tenant = mock(TenantGuard.class);
        var guard = new GuestSessionGuard(tokens, sessions, tenant);
        interceptor =
                new GuestWebSocketInterceptor(
                        mock(JwtUtil.class),
                        mock(AccountUserDetailsService.class),
                        sessions,
                        guard,
                        tenant);
        attributes = new HashMap<>();
        var restaurant = new Restaurant();
        restaurant.setId(3L);
        var branch = new Branch();
        branch.setId(1L);
        branch.setRestaurant(restaurant);
        session =
                TableSession.builder()
                        .id(1L)
                        .branch(branch)
                        .table(DiningTable.builder().id(1L).build())
                        .status(TableSessionStatus.SERVING)
                        .build();
        token = tokens.issue(session);
    }

    private Message<byte[]> frame(StompCommand command, String destination, String credential) {
        var accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("test-ws");
        accessor.setSessionAttributes(attributes);
        if (destination != null) accessor.setDestination(destination);
        if (credential != null) accessor.setNativeHeader("X-Guest-Token", credential);
        if (command == StompCommand.SUBSCRIBE) accessor.setNativeHeader("X-Guest-Session", "1");
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void connect() {
        interceptor.preSend(frame(StompCommand.CONNECT, null, token), mock(MessageChannel.class));
    }

    @Test
    void anonymousConnectionsAreRejected() {
        assertThatThrownBy(
                        () ->
                                interceptor.preSend(
                                        frame(StompCommand.CONNECT, null, null),
                                        mock(MessageChannel.class)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void guestCanSubscribeToOwnSession() {
        connect();
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        assertThatCode(
                        () ->
                                interceptor.preSend(
                                        frame(
                                                StompCommand.SUBSCRIBE,
                                                "/topic/session/1/item-status",
                                                null),
                                        mock(MessageChannel.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void guestCanSubscribeToSessionChangesAndOwnRestaurantMenu() {
        connect();
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        for (String destination :
                List.of("/topic/session/1/session-updated", "/topic/restaurant/3/menu-updated")) {
            assertThatCode(
                            () ->
                                    interceptor.preSend(
                                            frame(StompCommand.SUBSCRIBE, destination, null),
                                            mock(MessageChannel.class)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void guestCannotSubscribeToAnotherSession() {
        connect();
        var other =
                TableSession.builder()
                        .id(2L)
                        .table(DiningTable.builder().id(2L).build())
                        .status(TableSessionStatus.SERVING)
                        .guestAccessVersion(session.getGuestAccessVersion())
                        .build();
        when(sessions.findById(2L)).thenReturn(Optional.of(other));
        assertThatThrownBy(
                        () ->
                                interceptor.preSend(
                                        frame(
                                                StompCommand.SUBSCRIBE,
                                                "/topic/session/2/item-status",
                                                null),
                                        mock(MessageChannel.class)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void guestCannotSubscribeToBranchOrWildcard() {
        connect();
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        for (String destination :
                List.of(
                        "/topic/branch/1/new-order",
                        "/topic/branch/1/table-open-request",
                        "/topic/branch/1/session-updated",
                        "/topic/restaurant/3/management-updated",
                        "/topic/restaurant/3/operations-updated",
                        "/topic/restaurant/4/menu-updated",
                        "/topic/platform/structure-updated",
                        "/topic/session/*/item-status",
                        "/topic/**"))
            assertThatThrownBy(
                            () ->
                                    interceptor.preSend(
                                            frame(StompCommand.SUBSCRIBE, destination, null),
                                            mock(MessageChannel.class)))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void closedSessionCannotOpenNewSubscription() {
        connect();
        session.setStatus(TableSessionStatus.CLOSED);
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        assertThatThrownBy(
                        () ->
                                interceptor.preSend(
                                        frame(
                                                StompCommand.SUBSCRIBE,
                                                "/topic/session/1/item-status",
                                                null),
                                        mock(MessageChannel.class)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void clientsCannotPublishToBroker() {
        connect();
        assertThatThrownBy(
                        () ->
                                interceptor.preSend(
                                        frame(
                                                StompCommand.SEND,
                                                "/topic/session/1/item-status",
                                                null),
                                        mock(MessageChannel.class)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
