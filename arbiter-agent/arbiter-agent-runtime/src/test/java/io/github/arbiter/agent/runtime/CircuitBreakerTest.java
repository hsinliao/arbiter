package io.github.arbiter.agent.runtime;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerTest {

    @Test
    void allowsOnlyOneHalfOpenProbe() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ofMillis(10));
        assertThrows(IllegalStateException.class, () -> breaker.call("model", () -> {
            throw new IllegalStateException("failure");
        }));
        Thread.sleep(20);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> probeFailure = new AtomicReference<>();
        Thread probe = new Thread(() -> {
            try {
                breaker.call("model", () -> {
                    entered.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                    return "ok";
                });
            } catch (Throwable error) {
                probeFailure.set(error);
            }
        });
        probe.start();

        assertTrue(entered.await(1, TimeUnit.SECONDS));
        assertThrows(CircuitBreaker.CircuitOpenException.class,
                () -> breaker.call("model", () -> "second probe"));
        release.countDown();
        probe.join(1_000);

        if (probeFailure.get() != null) {
            throw new AssertionError("Half-open probe failed", probeFailure.get());
        }
    }
}
