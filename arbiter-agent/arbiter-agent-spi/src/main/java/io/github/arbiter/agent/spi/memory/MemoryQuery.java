package io.github.arbiter.agent.spi.memory;

import java.util.Set;

public record MemoryQuery(
        String namespace,
        String text,
        int limit,
        Set<MemoryType> types,
        Embedding embedding
) {
    public MemoryQuery {
        namespace = namespace == null || namespace.isBlank() ? "default" : namespace;
        text = text == null ? "" : text;
        limit = limit <= 0 ? 5 : limit;
        types = types == null || types.isEmpty()
                ? Set.of(MemoryType.SHORT_TERM, MemoryType.WORKING, MemoryType.LONG_TERM)
                : Set.copyOf(types);
    }

    public MemoryQuery(String namespace, String text, int limit, Set<MemoryType> types) {
        this(namespace, text, limit, types, null);
    }
}
