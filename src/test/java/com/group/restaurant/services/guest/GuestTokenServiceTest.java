package com.group.restaurant.services.guest;

import static org.assertj.core.api.Assertions.*;

import com.group.restaurant.models.TableSession;
import java.time.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class GuestTokenServiceTest {
    private final String secret =
            Base64.getEncoder().encodeToString("a-test-key-with-at-least-32-characters".getBytes());
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC);
    private final GuestTokenService service = new GuestTokenService(secret, clock);

    @Test
    void validTokenWorksOnlyForItsSession() {
        TableSession session = TableSession.builder().id(1L).build();
        String token = service.issue(session);
        assertThatCode(() -> service.verify(token, session)).doesNotThrowAnyException();
        TableSession other =
                TableSession.builder()
                        .id(2L)
                        .guestAccessVersion(session.getGuestAccessVersion())
                        .build();
        assertThatThrownBy(() -> service.verify(token, other))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void expiredTokenFails() {
        TableSession session = TableSession.builder().id(1L).build();
        String token = service.issue(session);
        GuestTokenService later =
                new GuestTokenService(secret, Clock.offset(clock, Duration.ofHours(25)));
        assertThatThrownBy(() -> later.verify(token, session))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void missingFakeAndTamperedTokensFail() {
        TableSession session = TableSession.builder().id(1L).build();
        String token = service.issue(session);
        for (String invalid : new String[] {null, "session-1", "x" + token})
            assertThatThrownBy(() -> service.verify(invalid, session))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void changingAccessVersionRevokesToken() {
        TableSession session = TableSession.builder().id(1L).build();
        String token = service.issue(session);
        session.setGuestAccessVersion("rotated");
        assertThatThrownBy(() -> service.verify(token, session))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
