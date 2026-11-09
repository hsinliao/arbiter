package io.github.arbiter.agent.core;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AgentTask(
        String id,
        String input,
        Instant createdAt,
        Map<String, Object> metadata
) {
    public AgentTask {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Task input must not be blank");
        }
        createdAt = createdAt == null ? Instant.now() : createdAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AgentTask of(String input) {
        return new AgentTask(null, input, Instant.now(), Map.of());
    }

    public static AgentTask of(String id, String input) {
        return new AgentTask(id, input, Instant.now(), Map.of());
    }
}
