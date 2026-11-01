package io.github.arbiter.client.config;

import java.nio.file.Path;
import java.time.Duration;

public record UnixSocketTransportConfig(Path socketPath, Duration readTimeout)
        implements TransportConfig {
    public UnixSocketTransportConfig {
        if (socketPath == null || socketPath.toString().isBlank()) {
            throw new IllegalArgumentException("socketPath must not be blank");
        }
        socketPath = socketPath.toAbsolutePath().normalize();
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("readTimeout must be positive");
        }
    }
}
