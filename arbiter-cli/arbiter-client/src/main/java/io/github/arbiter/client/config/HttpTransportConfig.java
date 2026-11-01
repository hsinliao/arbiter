package io.github.arbiter.client.config;

import java.net.URI;
import java.time.Duration;

/**
 * HTTP-specific transport settings. TLS belongs here and is deliberately not
 * part of the transport-neutral client config.
 */
public record HttpTransportConfig(URI baseUri, Duration connectTimeout,
                                  Duration readTimeout, Duration poolAcquireTimeout,
                                  int poolPermits, TlsClientConfig tls)
        implements TransportConfig {
    public HttpTransportConfig {
        if (baseUri == null || baseUri.getScheme() == null
                || (!"http".equalsIgnoreCase(baseUri.getScheme())
                && !"https".equalsIgnoreCase(baseUri.getScheme()))
                || baseUri.getHost() == null) {
            throw new IllegalArgumentException("Base URL must be an absolute HTTP(S) URL");
        }
        if (baseUri.getRawQuery() != null || baseUri.getRawFragment() != null) {
            throw new IllegalArgumentException("Base URL must not contain a query or fragment");
        }
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        requirePositive(poolAcquireTimeout, "poolAcquireTimeout");
        if (poolPermits < 1 || poolPermits > 64) {
            throw new IllegalArgumentException("poolPermits must be between 1 and 64");
        }
        if (tls == null) {
            throw new IllegalArgumentException("tls must not be null");
        }
        if (tls.enabled() && !"https".equalsIgnoreCase(baseUri.getScheme())) {
            throw new IllegalArgumentException(
                    "TLS configuration requires an https base URL");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
