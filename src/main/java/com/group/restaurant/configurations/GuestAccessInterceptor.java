package com.group.restaurant.configurations;

import static org.springframework.http.HttpStatus.*;

import com.group.restaurant.repositories.*;
import com.group.restaurant.services.guest.*;
import jakarta.servlet.http.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
@RequiredArgsConstructor
public class GuestAccessInterceptor implements HandlerInterceptor {
    private final GuestSessionGuard guard;
    private final TableSessionRepository sessions;
    private final OrderDetailRepository details;
    private final InvoiceRepository invoices;
    private final GuestRateLimiter limiter;

    @Override
    @Transactional(readOnly = true)
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) return true;
        String pattern =
                (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null) return true;
        @SuppressWarnings("unchecked")
        Map<String, String> vars =
                (Map<String, String>)
                        request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Long sessionId = null;
        if (pattern.matches(
                ".*/table-sessions/\\{id\\}(?:/(?:bill-preview|request-payment|call-staff))?")) {
            sessionId = Long.valueOf(vars.get("id"));
        } else if (pattern.endsWith("/table-sessions/table/{tableId}/open")) {
            sessionId =
                    sessions.findActiveSessionByTableId(Long.valueOf(vars.get("tableId")))
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    NOT_FOUND, "Bàn chưa có phiên hoạt động."))
                            .getId();
        } else if (pattern.endsWith("/orders/session/{tableSessionId}")
                || pattern.endsWith("/order-details/session/{tableSessionId}")) {
            sessionId = Long.valueOf(vars.get("tableSessionId"));
        } else if (pattern.contains("/invoices/session/{")) {
            sessionId =
                    Long.valueOf(
                            vars.containsKey("tableSessionId")
                                    ? vars.get("tableSessionId")
                                    : vars.get("sessionId"));
        } else if (pattern.matches(".*/invoices/\\{id\\}(?:/qr-code)?")
                && "GET".equals(request.getMethod())) {
            sessionId =
                    invoices.findById(Long.valueOf(vars.get("id")))
                            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND))
                            .getTableSession()
                            .getId();
        } else if (pattern.matches(".*/order-details/\\{id\\}/(?:cancel|staff-call)")) {
            sessionId =
                    details.findById(Long.valueOf(vars.get("id")))
                            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND))
                            .getOrder()
                            .getTableSession()
                            .getId();
        }
        if (sessionId != null) {
            boolean write = !"GET".equals(request.getMethod());
            guard.require(sessionId, write);
            if (write && request.getHeader("X-Guest-Token") != null) {
                limiter.check("action:" + request.getRemoteAddr() + ":" + sessionId, 15, 60_000);
            }
            response.setHeader("Cache-Control", "no-store");
        }
        return true;
    }
}
