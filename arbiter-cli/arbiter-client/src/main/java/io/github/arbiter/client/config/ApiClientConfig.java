package io.github.arbiter.client.config;

import java.time.Duration;

/**
 * Transport-independent client behavior: API prefix and retry policy.
 */
public record ApiClientConfig(String apiPrefix, int maxRetries,
                              Duration retryBaseDelay) {
    public ApiClientConfig {
        if (apiPrefix == null || apiPrefix.isBlank() || !apiPrefix.startsWith("/")) {
            throw new IllegalArgumentException("API prefix must start with '/'");
        }
        if (maxRetries < 0 || maxRetries > 10) {
            throw new IllegalArgumentException("maxRetries must be between 0 and 10");
        }
        if (retryBaseDelay == null || retryBaseDelay.isNegative()
                || retryBaseDelay.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException(
                    "retryBaseDelay must be between 0 and 30 seconds");
        }
    }
}
