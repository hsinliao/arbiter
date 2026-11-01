package io.github.arbiter.server.health;

import io.github.arbiter.api.crypto.CryptoKeys;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;

@Component
public final class SecurityMaterialHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        try {
            if (CryptoKeys.path() == null || !Files.isRegularFile(CryptoKeys.path())) {
                return Health.down().withDetail("reason", "crypto key is missing").build();
            }
            CryptoKeys.active();
            return Health.up().withDetail("cryptoKey", CryptoKeys.path().toString()).build();
        } catch (java.io.IOException | RuntimeException failure) {
            return Health.down(failure).build();
        }
    }
}
