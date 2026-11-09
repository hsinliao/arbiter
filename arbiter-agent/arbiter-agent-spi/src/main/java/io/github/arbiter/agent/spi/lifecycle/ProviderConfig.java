package io.github.arbiter.agent.spi.lifecycle;

import java.util.Map;

public record ProviderConfig(Map<String, Object> properties) {
    public ProviderConfig {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public static ProviderConfig empty() {
        return new ProviderConfig(Map.of());
    }
}
