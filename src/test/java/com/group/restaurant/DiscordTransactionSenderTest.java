package com.group.restaurant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.group.restaurant.models.SepayDiscordNotice;
import com.group.restaurant.repositories.SepayDiscordNoticeRepository;
import com.group.restaurant.services.DiscordTransactionSender;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DiscordTransactionSenderTest {
    @Test
    void sendsOnlyClaimedNoticesAndKeepsFailuresOutOfPaymentFlow() {
        String url = "https://discord.com/api/webhooks/123/test-token";
        var notices = mock(SepayDiscordNoticeRepository.class);
        var httpBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(httpBuilder).build();
        var configuredBuilder = mock(RestClient.Builder.class);
        when(configuredBuilder.requestFactory(any())).thenReturn(configuredBuilder);
        when(configuredBuilder.build()).thenReturn(httpBuilder.build());
        var sender = new DiscordTransactionSender(notices, configuredBuilder, url);

        when(notices.findTop25ByAttemptedAtIsNullOrderByTransactionIdAsc())
                .thenReturn(
                        List.of(
                                new SepayDiscordNotice(1L, "First payment"),
                                new SepayDiscordNotice(2L, "Already claimed"),
                                new SepayDiscordNotice(3L, "Second payment")));
        when(notices.claim(eq(1L), any())).thenReturn(1);
        when(notices.claim(eq(3L), any())).thenReturn(1);
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .json(
                                        "{\"content\":\"First payment\",\"allowed_mentions\":{\"parse\":[]}}"))
                .andRespond(withNoContent());
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        sender.sendQueuedNotices();

        server.verify();
        verify(notices).markSent(eq(1L), any());
        verify(notices, never()).markSent(eq(3L), any());
    }
}
