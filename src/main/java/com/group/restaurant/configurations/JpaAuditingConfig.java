package com.group.restaurant.configurations;

import com.group.restaurant.configurations.jwt.AccountUserDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

/**
 * Cấu hình JPA Auditing.
 *
 * VẤN ĐỀ TRƯỚC ĐÂY: `@EnableJpaAuditing` được bật trên class Application nhưng KHÔNG có
 * bean `AuditorAware` nào → Spring Data không biết "ai" đang thao tác, nên `@CreatedBy`
 * (`create_by`) và `@LastModifiedBy` (`modified_by`) của {@code BaseEntity} LUÔN null
 * trên toàn bộ 27 entity. Mọi bản ghi trong DB không truy được ai tạo/sửa.
 *
 * Ba nguồn thao tác trong hệ thống này, cần phân biệt được trong cột audit:
 *  - Nhân viên đã đăng nhập → username của tài khoản.
 *  - Khách quét QR (không đăng nhập, nhưng vẫn ghi dữ liệu: tạo phiên bàn, gửi order)
 *    → "guest": có HTTP request nhưng không có Authentication.
 *  - Scheduler ({@code TableSessionScheduler}, {@code StockResetScheduler})
 *    → "system": không có HTTP request nào.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    public static final String SYSTEM_AUDITOR = "system";
    public static final String GUEST_AUDITOR = "guest";

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of(currentAuditor());
    }

    private static String currentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getPrincipal() instanceof AccountUserDetails details) {
            return details.getUsername();
        }

        // Không có tài khoản: phân biệt khách (trong 1 HTTP request) với scheduler (ngoài request).
        return RequestContextHolder.getRequestAttributes() != null ? GUEST_AUDITOR : SYSTEM_AUDITOR;
    }
}
