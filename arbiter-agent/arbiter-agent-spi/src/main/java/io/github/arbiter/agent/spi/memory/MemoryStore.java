package io.github.arbiter.agent.spi.memory;

import io.github.arbiter.agent.spi.invocation.InvocationContext;

import java.util.List;

public interface MemoryStore {

    void upsert(MemoryRecord record, InvocationContext context);

    List<MemoryMatch> search(MemoryQuery query, InvocationContext context);

    void delete(String namespace, InvocationContext context);

    default void delete(String namespace, String id, InvocationContext context) {
        throw new UnsupportedOperationException("Record deletion is not supported");
    }
}
