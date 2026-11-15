package com.group.restaurant.configurations;

import com.group.restaurant.configurations.jwt.AuthTokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;

import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthTokenFilter authTokenFilter;

    /**
     * Prefix API lấy từ cấu hình (`api.prefix`) thay vì hard-code "/api/v1".
     * Trước đây danh sách public hard-code prefix — đổi API_PREFIX là mọi pattern
     * hết khớp (may là fail-closed, nhưng vẫn là bẫy cấu hình).
     */
    @Value("${api.prefix}")
    private String apiPrefix;

    @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://192.168.1.*:*,http://127.0.0.1:*}")
    private String allowedOriginPatterns;

    /**
     * ĐƯỜNG DẪN CÔNG KHAI — nguyên tắc: chỉ mở ĐÚNG endpoint mà KHÁCH KHÔNG ĐĂNG NHẬP
     * cần dùng, theo đúng METHOD, không dùng wildcard `/**` cho cả controller.
     *
     * Trước đây danh sách này mở `/orders/**`, `/invoices/**`, `/payments/**`,
     * `/table-sessions/**`, `/branches/**` → bất kỳ ai cũng gọi được
     * `POST /payments` (thanh toán khống → hóa đơn tự chuyển PAID),
     * `POST /invoices/{id}/cancel`, `DELETE /table-sessions/{id}`,
     * `GET /invoices/branch/{id}` (lộ doanh thu mọi chi nhánh).
     *
     * Đối chiếu với FE: xem `src/features/customer/api/index.ts` — 10 bước của luồng khách.
     */

    /** GET công khai — chỉ đọc, phục vụ luồng quét QR → xem thực đơn → xem hóa đơn. */
    private static final String[] PUBLIC_GET = {
            "/guest/table",
            // Quét QR bàn
            "/tables/branch/*/qr/*",        // lấy thông tin bàn theo mã QR
            "/tables/*/qr-url",             // xem URL QR (để in)
            "/tables/*/qr-image",           // xem ảnh QR (để in / quét)

            // Phiên bàn của chính khách (theo id phiên / id bàn)
            "/table-sessions/*",            // theo dõi trạng thái phiên (chờ mở bàn → SERVING)
            "/table-sessions/table/*/open", // phục hồi phiên khi khách F5 / mở lại link

            // Thực đơn chi nhánh
            // LƯU Ý: KHÔNG mở "/branches/*" — `BranchController.getBranchById` là
            // @PreAuthorize("isAuthenticated()") và chỉ màn Thu ngân dùng (useBranch).
            "/branches/*/menu",
            "/branches/*/menu/overview",
            "/dishes/*",
            "/dishes/branch/*",
            "/dishes/category/*",
            "/dish-option-groups/dish/*",
            "/option-items/group/*",
            "/branch-dish-stocks/branch/*/out-of-stock",

            // Món đã gọi của phiên
            "/orders/session/*",
            "/order-details/session/*",
            "/table-sessions/*/bill-preview",  // tạm tính theo phiên (BR-13) — cùng nhóm với order-details/session

            // Hóa đơn + QR chuyển khoản của phiên (khách poll chờ Thu ngân xác nhận)
            "/invoices/*",
            "/invoices/*/qr-code",
            "/invoices/session/*",
            "/invoices/session/*/qr-code",

            // Khuyến mãi chi nhánh (khách quét QR và nhân viên tra cứu)
            "/promotions/branch/*/active",
            "/promotions/branch/*/code/*",
            "/promotion-branches/branch/*",
    };

    /** POST công khai — các hành động khách tự làm được trên thiết bị của mình. */
    private static final String[] PUBLIC_POST = {
            "/auth/login",
            "/auth/refresh-token",

            "/guest/access",                     // quét QR
            // Staff use POST /table-sessions; guests use POST /guest/access.
            "/table-sessions/*/request-payment",// khách bấm "Yêu cầu tính tiền"
            "/table-sessions/*/call-staff",     // khách bấm "Gọi nhân viên"
            "/orders",                          // khách gửi giỏ hàng
            "/order-details/*/cancel",          // khách tự hủy món chưa vào bếp
            "/order-details/*/staff-call",      // khách bấm "Báo nhân viên" (FR-CUS-07)
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Cho phép async và error dispatch để không bị AuthorizationFilter chặn lại sau khi xử lý
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        // WebSocket / STOMP handshake (A3)
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, prefixed(PUBLIC_GET)).permitAll()
                        .requestMatchers(HttpMethod.POST, prefixed(PUBLIC_POST)).permitAll()
                        // Mọi thứ còn lại — kể cả POST /payments, POST /invoices,
                        // /invoices/branch/**, /orders/{id}/cancel, DELETE /** — phải đăng nhập.
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    /** Gắn `api.prefix` vào từng pattern tương đối. */
    private String[] prefixed(String[] paths) {
        return Arrays.stream(paths).map(p -> apiPrefix + p).toArray(String[]::new);
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // Cho phép tất cả IP trong mạng local (development only)
        config.setAllowedOriginPatterns(Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        // "Refresh-Token": AuthController.refreshToken đọc token từ header này.
        // Thiếu nó trong allowedHeaders → browser chặn ngay ở preflight → luồng
        // refresh token không bao giờ chạy được từ FE (chỉ chạy khi test bằng Postman).
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Refresh-Token", "X-Guest-Token"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
