package io.github.arbiter.server.service;

import io.github.arbiter.api.dto.SystemStatusResponse;

import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public final class StatusService {
    private final Clock clock;

    public StatusService(Clock clock) {
        this.clock = clock;
    }

    public SystemStatusResponse status() {
        String version = StatusService.class.getPackage().getImplementationVersion();
        return new SystemStatusResponse("UP",
                version == null ? "development" : version,
                clock.instant().toString());
    }
}
