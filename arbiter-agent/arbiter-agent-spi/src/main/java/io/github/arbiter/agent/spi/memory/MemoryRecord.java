package io.github.arbiter.agent.spi.memory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MemoryRecord(
        String id,
        String namespace,
        MemoryType type,
        String content,
        Map<String, Object> metadata,
        Embedding embedding,
        Instant createdAt
) {
    public MemoryRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Memory record id is required");
        }
        namespace = namespace == null || namespace.isBlank() ? "default" : namespace;
        type = type == null ? MemoryType.LONG_TERM : type;
        content = content == null ? "" : content;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static MemoryRecord text(String namespace, MemoryType type, String content) {
        return new MemoryRecord(
                UUID.randomUUID().toString(), namespace, type, content, Map.of(), null, Instant.now());
    }

    public static MemoryRecord create(
            String id,
            String namespace,
            MemoryType type,
            String content,
            Map<String, Object> metadata,
            Embedding embedding
    ) {
        return new MemoryRecord(id, namespace, type, content, metadata, embedding, Instant.now());
    }
}
