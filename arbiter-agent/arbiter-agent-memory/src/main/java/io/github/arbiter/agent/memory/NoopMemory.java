package io.github.arbiter.agent.memory;

import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.memory.MemoryMatch;
import io.github.arbiter.agent.spi.memory.MemoryQuery;
import io.github.arbiter.agent.spi.memory.MemoryRecord;
import io.github.arbiter.agent.spi.memory.MemoryStore;

import java.util.List;

/**
 * Explicit no-op memory used when an application has not opted into memory storage.
 */
public final class NoopMemory implements MemoryStore {

    @Override
    public void upsert(MemoryRecord record, InvocationContext context) {
    }

    @Override
    public List<MemoryMatch> search(MemoryQuery query, InvocationContext context) {
        return List.of();
    }

    @Override
    public void delete(String namespace, InvocationContext context) {
    }

    @Override
    public void delete(String namespace, String id, InvocationContext context) {
    }
}
