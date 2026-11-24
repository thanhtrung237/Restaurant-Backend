package com.group.restaurant.configurations;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI restaurantOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Restaurant API").version("v1")
                        .description("Restaurant management API. Log in through /api/v1/auth/login, "
                                + "then paste the access token into Authorize. Guest table endpoints "
                                + "may additionally require X-Guest-Token. Role/tenant checks still apply."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Paste the access token without the Bearer prefix.")));
    }

    @Bean
    public OpenApiCustomizer restaurantOperationSecurity(SecurityConfig security) {
        return api -> {
            if (api.getPaths() == null) return;
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                if (security.isPublicApiPath(method.name(), path)) {
                    operation.setSecurity(List.of());
                } else {
                    operation.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth")));
                }
            }));
        };
    }
}
