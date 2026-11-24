package com.group.restaurant.configurations;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * A3 — Cấu hình STOMP WebSocket (T-01: cập nhật trạng thái ≤ 5 giây).
 *
 * Client kết nối: ws://<host>/ws
 * Subscribe topic:
 *   - /topic/branch/{branchId}/new-order     — order mới (cho Phục vụ)
 *   - /topic/session/{sessionId}/item-status — dòng món đổi trạng thái (cho khách + Phục vụ)
 */
@Configuration
@EnableWebSocketMessageBroker
@lombok.RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final GuestWebSocketInterceptor guestWebSocketInterceptor;

    @org.springframework.beans.factory.annotation.Value(
            "${app.cors.allowed-origin-patterns:http://localhost:*,http://192.168.1.*:*,http://127.0.0.1:*}")
    private String allowedOriginPatterns;

    @Override
    public void configureClientInboundChannel(
            org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(guestWebSocketInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefix cho server → client push
        registry.enableSimpleBroker("/topic");
        // Prefix cho client → server gửi message (dùng nếu cần 2 chiều)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        java.util.Arrays.stream(allowedOriginPatterns.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isEmpty())
                                .toArray(String[]::new))
                .withSockJS(); // fallback cho trình duyệt không hỗ trợ native WS
    }
}
