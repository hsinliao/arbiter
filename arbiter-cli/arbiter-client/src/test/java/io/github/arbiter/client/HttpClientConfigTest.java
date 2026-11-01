package io.github.arbiter.client;

import io.github.arbiter.client.config.HttpClientConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpClientConfigTest {
    @Test
    void rejectsUnboundedRetryAndPoolSettings() {
        assertThrows(IllegalArgumentException.class, () -> config(11, 4, 150));
        assertThrows(IllegalArgumentException.class, () -> config(2, 65, 150));
        assertThrows(IllegalArgumentException.class, () -> config(2, 4, 30_001));
    }

    private static HttpClientConfig config(int retries, int permits, long delayMillis) {
        return new HttpClientConfig(URI.create("http://localhost:8080"), "/api/v1",
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
                permits, retries, Duration.ofMillis(delayMillis));
    }
}
