package com.group.restaurant.services.guest;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

/** Bounded, synchronized fixed windows for one backend instance. Use a shared store when scaling out. */
@Component
public class GuestRateLimiter {
    private record Window(long expiresAt, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public GuestRateLimiter() { this(Clock.systemUTC()); }
    GuestRateLimiter(Clock clock) { this.clock = clock; }

    public synchronized void check(String key, int limit, long windowMillis) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= 20_000) reject();
            window = new Window(now + windowMillis, 0);
        }
        if (window.count() >= limit) reject();
        windows.put(key, new Window(window.expiresAt(), window.count() + 1));
    }
    private void reject() {
        throw new ResponseStatusException(TOO_MANY_REQUESTS, "Bạn gửi yêu cầu quá nhanh. Vui lòng đợi một phút rồi thử lại.");
    }
}
