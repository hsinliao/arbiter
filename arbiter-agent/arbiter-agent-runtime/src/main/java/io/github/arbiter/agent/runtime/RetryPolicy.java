package io.github.arbiter.agent.runtime;

import io.github.arbiter.agent.spi.error.ProviderException;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final double multiplier;
    private final Duration maxDelay;

    public RetryPolicy(int maxAttempts, Duration initialDelay, double multiplier, Duration maxDelay) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay == null ? Duration.ZERO : initialDelay;
        if (this.initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay must not be negative");
        }
        if (multiplier <= 0) {
            throw new IllegalArgumentException("multiplier must be positive");
        }
        this.multiplier = multiplier;
        this.maxDelay = maxDelay == null ? Duration.ofSeconds(10) : maxDelay;
        if (this.maxDelay.isNegative()) {
            throw new IllegalArgumentException("maxDelay must not be negative");
        }
    }

    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO, 1.0, Duration.ZERO);
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(2));
    }

    public <T> T execute(Supplier<T> operation, Predicate<Throwable> shouldRetry) {
        Objects.requireNonNull(operation, "operation");
        Predicate<Throwable> predicate = shouldRetry == null ? ignored -> true : shouldRetry;
        RuntimeException last = null;
        Duration delay = initialDelay;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException error) {
                last = error;
                if (attempt == maxAttempts || !predicate.test(error)) {
                    throw error;
                }
                Duration wait = error instanceof ProviderException providerError
                        && providerError.retryAfter() != null
                        ? providerError.retryAfter() : delay;
                if (wait.compareTo(maxDelay) > 0) {
                    wait = maxDelay;
                }
                sleep(wait);
                delay = nextDelay(delay);
            }
        }
        throw last == null ? new IllegalStateException("Retry operation did not run") : last;
    }

    private Duration nextDelay(Duration current) {
        long next = Math.max(1L, (long) (current.toMillis() * multiplier));
        return Duration.ofMillis(Math.min(next, maxDelay.toMillis()));
    }

    private static void sleep(Duration delay) {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", error);
        }
    }
}
