package io.github.arbiter.agent.spi.lifecycle;

import java.util.Map;

public record ProviderHealth(boolean healthy, String message, Map<String, Object> details) {
    public ProviderHealth {
        message = message == null ? "" : message;
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ProviderHealth up() {
        return new ProviderHealth(true, "", Map.of());
    }

    public static ProviderHealth down(String message) {
        return new ProviderHealth(false, message, Map.of());
    }
}
