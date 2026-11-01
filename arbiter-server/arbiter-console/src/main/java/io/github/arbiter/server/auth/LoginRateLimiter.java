package io.github.arbiter.server.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LoginRateLimiter {
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginRateLimiter(int maxFailures, Duration window, Clock clock) {
        if (maxFailures < 1) {
            throw new IllegalArgumentException("maxFailures must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    public boolean isLimited(String key) {
        Instant cutoff = clock.instant().minus(window);
        Deque<Instant> timestamps = failures.get(key);
        if (timestamps == null) {
            return false;
        }
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(cutoff)) {
                timestamps.removeFirst();
            }
            if (timestamps.isEmpty()) {
                failures.remove(key, timestamps);
                return false;
            }
            return timestamps.size() >= maxFailures;
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> timestamps = failures.computeIfAbsent(key,
                ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            timestamps.addLast(clock.instant());
        }
        if (failures.size() > MAX_TRACKED_KEYS) {
            evictOldEntries();
        }
    }

    private void evictOldEntries() {
        Instant cutoff = clock.instant().minus(window);
        failures.forEach((key, timestamps) -> {
            synchronized (timestamps) {
                while (!timestamps.isEmpty()
                        && !timestamps.peekFirst().isAfter(cutoff)) {
                    timestamps.removeFirst();
                }
                if (timestamps.isEmpty()) {
                    failures.remove(key, timestamps);
                }
            }
        });
        int excess = failures.size() - MAX_TRACKED_KEYS;
        if (excess <= 0) {
            return;
        }
        for (String key : failures.keySet()) {
            if (excess-- <= 0) {
                break;
            }
            failures.remove(key);
        }
    }

    public void clear(String key) {
        failures.remove(key);
    }
}
