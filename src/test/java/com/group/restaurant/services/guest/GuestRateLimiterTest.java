package com.group.restaurant.services.guest;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GuestRateLimiterTest {
    @Test
    void rejectsOverLimitAndSeparatesKeys() {
        GuestRateLimiter limiter = new GuestRateLimiter();
        limiter.check("a", 1, 60_000);
        assertThatThrownBy(() -> limiter.check("a", 1, 60_000))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatCode(() -> limiter.check("b", 1, 60_000)).doesNotThrowAnyException();
    }

    @Test
    void expiredWindowAllowsRequestsAgain() {
        Clock clock = org.mockito.Mockito.mock(Clock.class);
        org.mockito.Mockito.when(clock.millis()).thenReturn(0L, 1001L);
        GuestRateLimiter limiter = new GuestRateLimiter(clock);
        limiter.check("a", 1, 1000);
        assertThatCode(() -> limiter.check("a", 1, 1000)).doesNotThrowAnyException();
    }

    @Test
    void concurrentRequestsCannotExceedLimit() throws Exception {
        GuestRateLimiter limiter = new GuestRateLimiter();
        AtomicInteger accepted = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 100; i++)
                tasks.add(
                        pool.submit(
                                () -> {
                                    try {
                                        limiter.check("same", 5, 60_000);
                                        accepted.incrementAndGet();
                                    } catch (
                                            org.springframework.web.server.ResponseStatusException
                                                    expected) {
                                    }
                                }));
            for (var task : tasks) task.get();
        }
        assertThat(accepted.get()).isEqualTo(5);
    }
}
