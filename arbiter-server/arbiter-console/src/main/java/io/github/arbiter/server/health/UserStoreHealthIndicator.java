package io.github.arbiter.server.health;

import io.github.arbiter.server.auth.FileUserRepository;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public final class UserStoreHealthIndicator implements HealthIndicator {
    private final FileUserRepository users;

    public UserStoreHealthIndicator(FileUserRepository users) {
        this.users = users;
    }

    @Override
    public Health health() {
        try {
            return Health.up()
                    .withDetail("file", users.path().toString())
                    .withDetail("users", users.findAll().size())
                    .build();
        } catch (RuntimeException failure) {
            return Health.down(failure).build();
        }
    }
}
