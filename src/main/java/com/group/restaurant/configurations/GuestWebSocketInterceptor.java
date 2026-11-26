package com.group.restaurant.configurations;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import com.group.restaurant.configurations.jwt.*;
import com.group.restaurant.enums.TokenType;
import com.group.restaurant.repositories.TableSessionRepository;
import com.group.restaurant.services.guest.GuestSessionGuard;
import com.group.restaurant.utils.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class GuestWebSocketInterceptor implements ChannelInterceptor {
    private final JwtUtil jwt;
    private final AccountUserDetailsService users;
    private final TableSessionRepository sessions;
    private final GuestSessionGuard guests;
    private final TenantGuard tenantGuard;

    @Override
    @Transactional(readOnly = true)
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;
        if (accessor.getCommand() == StompCommand.CONNECT) {
            String staff = accessor.getFirstNativeHeader("Authorization");
            String guest = accessor.getFirstNativeHeader("X-Guest-Token");
            if (staff != null && staff.startsWith("Bearer ")) {
                String token = staff.substring(7);
                authenticateStaff(token);
                accessor.getSessionAttributes().put("staffToken", token);
            } else if (guest != null && !guest.isBlank()) {
                accessor.getSessionAttributes().put("guestToken", guest);
            } else deny();
        } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            var attributes = accessor.getSessionAttributes();
            String staff = attributes == null ? null : (String) attributes.get("staffToken");
            var previous = SecurityContextHolder.getContext();
            try {
                SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
                if (staff != null)
                    SecurityContextHolder.getContext().setAuthentication(authenticateStaff(staff));
                if (destination != null
                        && destination.matches("/topic/session/[0-9]+/(item-status|session-updated)")) {
                    Long id = Long.valueOf(destination.split("/")[3]);
                    if (staff != null) guests.require(id, false);
                    else {
                        String token =
                                attributes == null ? null : (String) attributes.get("guestToken");
                        guests.requireGuest(
                                token,
                                sessions.findById(id)
                                        .orElseThrow(() -> new ResponseStatusException(FORBIDDEN)),
                                destination.endsWith("/item-status"));
                    }
                } else if (destination != null
                        && destination.matches("/topic/restaurant/[0-9]+/menu-updated")) {
                    Long restaurantId = Long.valueOf(destination.split("/")[3]);
                    if (staff != null) {
                        tenantGuard.assertRestaurant(restaurantId);
                    } else {
                        String guestSession = accessor.getFirstNativeHeader("X-Guest-Session");
                        if (guestSession == null || !guestSession.matches("[0-9]{1,18}")) deny();
                        var session =
                                sessions.findById(Long.valueOf(guestSession))
                                        .orElseThrow(() -> new ResponseStatusException(FORBIDDEN));
                        guests.requireGuest(
                                attributes == null ? null : (String) attributes.get("guestToken"),
                                session,
                                false);
                        if (!restaurantId.equals(session.getBranch().getRestaurant().getId())) deny();
                    }
                } else if (staff != null
                        && destination != null
                        && destination.matches("/topic/restaurant/[0-9]+/management-updated")) {
                    tenantGuard.assertRestaurant(Long.valueOf(destination.split("/")[3]));
                } else if (staff != null
                        && destination != null
                        && destination.matches("/topic/restaurant/[0-9]+/operations-updated")) {
                    var auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth.getAuthorities().stream()
                            .noneMatch(authority -> ("ROLE_" + TenantGuard.ROLE_MANAGER)
                                    .equals(authority.getAuthority()))) deny();
                    tenantGuard.assertRestaurant(Long.valueOf(destination.split("/")[3]));
                } else if (staff != null
                        && "/topic/platform/structure-updated".equals(destination)
                        && tenantGuard.isPlatformAdmin()) {
                    return message;
                } else if (staff != null
                        && destination != null
                        && destination.matches(
                                "/topic/branch/[0-9]+/(new-order|payment-request|staff-call|takeaway-updated|table-open-request|session-updated)")) {
                    tenantGuard.assertBranchAssigned(Long.valueOf(destination.split("/")[3]));
                } else deny();
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        } else if (accessor.getCommand() == StompCommand.SEND) {
            // This application only pushes server events; clients cannot publish broker messages.
            deny();
        }
        return message;
    }

    private UsernamePasswordAuthenticationToken authenticateStaff(String token) {
        jwt.validateToken(token, TokenType.ACCESS_TOKEN);
        Long accountId = jwt.getAccountIdFromToken(token, TokenType.ACCESS_TOKEN);
        if (accountId == null) deny();
        var user = users.loadByAccountId(accountId);
        if (!user.isEnabled()) deny();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private void deny() {
        throw new ResponseStatusException(
                FORBIDDEN, "Không có quyền nhận thông báo của bàn/chi nhánh này.");
    }
}
