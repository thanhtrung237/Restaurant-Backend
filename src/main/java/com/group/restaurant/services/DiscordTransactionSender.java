package com.group.restaurant.services;

import com.group.restaurant.models.SepayDiscordNotice;
import com.group.restaurant.repositories.SepayDiscordNoticeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DiscordTransactionSender {
    private final SepayDiscordNoticeRepository notices;
    private final RestClient restClient;
    private final String webhookUrl;

    public DiscordTransactionSender(SepayDiscordNoticeRepository notices, RestClient.Builder builder,
                                    @Value("${DISCORD_TRANSACTION_WEBHOOK_URL:}") String webhookUrl) {
        this.notices = notices;
        String configuredUrl = webhookUrl.trim();
        boolean valid = configuredUrl.matches("https://discord\\.com/api/webhooks/[0-9]+/[A-Za-z0-9_-]+");
        if (!valid && !configuredUrl.isEmpty()) log.warn("Invalid DISCORD_TRANSACTION_WEBHOOK_URL; notifications disabled");
        this.webhookUrl = valid ? configuredUrl : "";
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder.requestFactory(requests).build();
    }

    public boolean enabled() {
        return !webhookUrl.isEmpty();
    }

    @Scheduled(fixedDelay = 5_000)
    public void sendQueuedNotices() {
        if (!enabled()) return;
        for (SepayDiscordNotice notice : notices.findTop25ByAttemptedAtIsNullOrderByTransactionIdAsc()) {
            long id = notice.getTransactionId();
            if (notices.claim(id, Instant.now()) == 0) continue;
            try {
                restClient.post().uri(webhookUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("content", notice.getMessage(), "allowed_mentions", Map.of("parse", List.of())))
                        .retrieve().toBodilessEntity();
                notices.markSent(id, Instant.now());
            } catch (RestClientException e) {
                // A timeout may occur after Discord accepted the message, so do not repost automatically.
                log.warn("Discord notification failed for SePay transaction {}", id);
            }
        }
    }
}
