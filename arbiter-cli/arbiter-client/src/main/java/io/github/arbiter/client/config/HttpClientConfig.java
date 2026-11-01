package io.github.arbiter.client.config;

import java.net.URI;
import java.time.Duration;

public record HttpClientConfig(URI baseUri, String apiPrefix, Duration connectTimeout,
                               Duration readTimeout, Duration poolAcquireTimeout,
                               int poolPermits, int maxRetries, Duration retryBaseDelay,
                               TlsClientConfig tls) {
    public HttpClientConfig {
        if (baseUri == null || baseUri.getScheme() == null
                || (!"http".equalsIgnoreCase(baseUri.getScheme())
                && !"https".equalsIgnoreCase(baseUri.getScheme()))
                || baseUri.getHost() == null) {
            throw new IllegalArgumentException("Base URL must be an absolute HTTP(S) URL");
        }
        if (baseUri.getRawQuery() != null || baseUri.getRawFragment() != null) {
            throw new IllegalArgumentException("Base URL must not contain a query or fragment");
        }
        if (apiPrefix == null || apiPrefix.isBlank() || !apiPrefix.startsWith("/")) {
            throw new IllegalArgumentException("API prefix must start with '/'");
        }
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        requirePositive(poolAcquireTimeout, "poolAcquireTimeout");
        if (poolPermits < 1 || poolPermits > 64) {
            throw new IllegalArgumentException("poolPermits must be between 1 and 64");
        }
        if (maxRetries < 0 || maxRetries > 10) {
            throw new IllegalArgumentException("maxRetries must be between 0 and 10");
        }
        if (retryBaseDelay == null || retryBaseDelay.isNegative()
                || retryBaseDelay.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException(
                    "retryBaseDelay must be between 0 and 30 seconds");
        }
        if (tls == null) {
            throw new IllegalArgumentException("tls must not be null");
        }
        if (tls.enabled() && !"https".equalsIgnoreCase(baseUri.getScheme())) {
            throw new IllegalArgumentException(
                    "TLS configuration requires an https base URL");
        }
    }

    public HttpClientConfig(URI baseUri, String apiPrefix, Duration connectTimeout,
                            Duration readTimeout, Duration poolAcquireTimeout,
                            int poolPermits, int maxRetries, Duration retryBaseDelay) {
        this(baseUri, apiPrefix, connectTimeout, readTimeout, poolAcquireTimeout,
                poolPermits, maxRetries, retryBaseDelay, TlsClientConfig.disabled());
    }

    public ApiClientConfig apiClientConfig() {
        return new ApiClientConfig(apiPrefix, maxRetries, retryBaseDelay);
    }

    public HttpTransportConfig httpTransportConfig() {
        return new HttpTransportConfig(baseUri, connectTimeout, readTimeout,
                poolAcquireTimeout, poolPermits, tls);
    }

    public static HttpClientConfig defaults(String server) {
        return new HttpClientConfig(
                URI.create(server),
                "/api/v1",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                4,
                2,
                Duration.ofMillis(150),
                TlsClientConfig.disabled()
        );
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
