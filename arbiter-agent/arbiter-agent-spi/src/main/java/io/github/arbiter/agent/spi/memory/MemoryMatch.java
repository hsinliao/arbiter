package io.github.arbiter.agent.spi.memory;

import java.util.Map;

public record MemoryMatch(MemoryRecord record, double score, Map<String, Object> provenance) {
    public MemoryMatch {
        if (record == null) {
            throw new IllegalArgumentException("Memory record is required");
        }
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
    }
}
