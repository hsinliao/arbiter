package io.github.arbiter.agent.memory;

import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.memory.MemoryMatch;
import io.github.arbiter.agent.spi.memory.MemoryQuery;
import io.github.arbiter.agent.spi.memory.MemoryRecord;
import io.github.arbiter.agent.spi.memory.MemoryStore;
import io.github.arbiter.agent.spi.memory.MemoryType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Three-tier memory: bounded short-term window, bounded working set and a long-term store.
 */
public class TieredMemory implements MemoryStore {

    private final int shortTermCapacity;
    private final int workingCapacity;
    private final InMemoryMemoryStore shortTerm = new InMemoryMemoryStore();
    private final Map<String, MemoryRecord> working = new LinkedHashMap<>(16, 0.75f, true);
    private final InMemoryMemoryStore longTerm;

    public TieredMemory() {
        this(20, 200, new InMemoryMemoryStore());
    }

    public TieredMemory(int shortTermCapacity, int workingCapacity, InMemoryMemoryStore longTerm) {
        if (shortTermCapacity <= 0 || workingCapacity <= 0) {
            throw new IllegalArgumentException("Memory capacities must be positive");
        }
        this.shortTermCapacity = shortTermCapacity;
        this.workingCapacity = workingCapacity;
        this.longTerm = longTerm == null ? new InMemoryMemoryStore() : longTerm;
    }

    @Override
    public synchronized void upsert(MemoryRecord record, InvocationContext context) {
        switch (record.type()) {
            case SHORT_TERM -> {
                shortTerm.upsert(record, context);
                trimShortTerm(record.namespace(), context);
            }
            case WORKING -> {
                working.put(record.namespace() + "\u0000" + record.id(), record);
                trimWorking(record.namespace());
            }
            case LONG_TERM -> longTerm.upsert(record, context);
        }
    }

    @Override
    public synchronized List<MemoryMatch> search(MemoryQuery query, InvocationContext context) {
        var queryTerms = InMemoryMemoryStore.terms(query.text());
        List<MemoryMatch> candidates = new ArrayList<>();
        candidates.addAll(shortTerm.search(query, context));
        candidates.addAll(working.values().stream()
                .filter(record -> record.namespace().equals(query.namespace()))
                .filter(record -> query.types().contains(record.type()))
                .map(record -> new MemoryMatch(
                        record, InMemoryMemoryStore.score(record, query, queryTerms),
                        Map.of("retriever", "working-memory")))
                .toList());
        candidates.addAll(longTerm.search(query, context));

        return candidates.stream()
                .distinct()
                .sorted(Comparator.comparingDouble(MemoryMatch::score).reversed()
                        .thenComparing(match -> match.record().createdAt(), Comparator.reverseOrder()))
                .limit(query.limit())
                .toList();
    }

    @Override
    public synchronized void delete(String namespace, InvocationContext context) {
        shortTerm.delete(namespace, context);
        working.entrySet().removeIf(entry -> entry.getValue().namespace().equals(namespace));
        longTerm.delete(namespace, context);
    }

    @Override
    public synchronized void delete(String namespace, String id, InvocationContext context) {
        shortTerm.delete(namespace, id, context);
        working.remove(namespace + "\u0000" + id);
        longTerm.delete(namespace, id, context);
    }

    public synchronized int shortTermSize(String namespace, InvocationContext context) {
        return shortTerm.search(new MemoryQuery(
                namespace, "", Integer.MAX_VALUE, Set.of(MemoryType.SHORT_TERM)), context).size();
    }

    private void trimShortTerm(String namespace, InvocationContext context) {
        List<MemoryRecord> records = shortTerm.search(new MemoryQuery(
                        namespace, "", Integer.MAX_VALUE, Set.of(MemoryType.SHORT_TERM)),
                        context).stream()
                .map(MemoryMatch::record)
                .toList();
        if (records.size() <= shortTermCapacity) {
            return;
        }
        records.stream()
                .sorted(Comparator.comparing(MemoryRecord::createdAt))
                .limit(records.size() - shortTermCapacity)
                .forEach(record -> shortTerm.delete(namespace, record.id(), context));
    }

    private void trimWorking(String namespace) {
        List<Map.Entry<String, MemoryRecord>> entries = working.entrySet().stream()
                .filter(entry -> entry.getValue().namespace().equals(namespace))
                .toList();
        int removeCount = entries.size() - workingCapacity;
        if (removeCount > 0) {
            entries.stream().limit(removeCount).forEach(entry -> working.remove(entry.getKey()));
        }
    }

}
