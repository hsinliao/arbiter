package io.github.arbiter.server.health;

import io.github.arbiter.server.config.ServerConfig;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public final class UnixSocketHealthIndicator implements HealthIndicator {
    private final ServerConfig config;

    public UnixSocketHealthIndicator(ServerConfig config) {
        this.config = config;
    }

    @Override
    public Health health() {
        if (!config.transport().unixEnabled()) {
            return Health.up().withDetail("enabled", false).build();
        }
        Path socket = config.transport().socketPath();
        if (socket == null || !Files.exists(socket)) {
            return Health.down().withDetail("socket", String.valueOf(socket)).build();
        }
        return Health.up().withDetail("socket", socket.toString()).build();
    }
}
