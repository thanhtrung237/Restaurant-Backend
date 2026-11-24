package com.group.restaurant;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableJpaAuditing đã chuyển sang JpaAuditingConfig để đi kèm bean AuditorAware
// (trước đây bật ở đây mà không có AuditorAware → create_by/modified_by luôn null).
@SpringBootApplication
@EnableScheduling
public class RestaurantBackendApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        Path dotenvPath = Paths.get(".env");
        if (Files.exists(dotenvPath)) {
            Dotenv dotenv = Dotenv.configure().filename(".env").load();

            dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
        }

        SpringApplication.run(RestaurantBackendApplication.class, args);
    }
}
