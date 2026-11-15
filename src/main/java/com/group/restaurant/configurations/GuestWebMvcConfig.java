package com.group.restaurant.configurations;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
@RequiredArgsConstructor
public class GuestWebMvcConfig implements WebMvcConfigurer {
    private final GuestAccessInterceptor interceptor;
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(interceptor); }
}
