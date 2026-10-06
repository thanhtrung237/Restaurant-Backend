package com.group.restaurant.configurations;

import com.group.restaurant.configurations.jwt.AuthTokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthTokenFilter authTokenFilter;

    private static final String[] PUBLIC_ENDPOINTS = {
            // Auth endpoints
            "/api/v1/auth/login",
            "/api/v1/auth/refresh-token",

            //  Quét QR bàn — khách không đăng nhập
            "/api/v1/tables/scan",
            //  Xem ảnh QR — public để in
            //  Xem URL QR và ảnh QR — public để in hoặc quét
            "/api/v1/tables/*/qr-url",
            "/api/v1/tables/*/qr-image",

            // Customer-facing endpoints (không cần đăng nhập)
            "/api/v1/tables/branch/*/qr/*",              // Lấy thông tin bàn theo QR
            "/api/v1/table-sessions/**",                 // Quản lý phiên bàn
            "/api/v1/orders/**",                         // Gọi món
            "/api/v1/order-details/**",                  // Chi tiết order
            "/api/v1/dishes/**",                         // Xem món ăn
            "/api/v1/dish-option-groups/**",             // Option groups của món
            "/api/v1/option-items/**",                   // Option items
            "/api/v1/branch-menu/**",                    // Menu chi nhánh
            "/api/v1/invoices/**",                       // Hóa đơn
            "/api/v1/payments/**",                       // Thanh toán
            "/api/v1/categories/**",                     // Danh mục món
            "/api/v1/branches/**",                       // Thông tin chi nhánh

            // WebSocket / STOMP (A3)
            "/ws/**",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // Cho phép tất cả IP trong mạng local (development only)
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://192.168.1.*:*",
                "http://127.0.0.1:*"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
