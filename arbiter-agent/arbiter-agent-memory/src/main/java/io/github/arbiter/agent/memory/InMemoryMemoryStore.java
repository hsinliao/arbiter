package io.github.arbiter.agent.memory;

import io.github.arbiter.agent.spi.memory.MemoryQuery;
import io.github.arbiter.agent.spi.memory.MemoryMatch;
import io.github.arbiter.agent.spi.memory.MemoryRecord;
import io.github.arbiter.agent.spi.memory.MemoryStore;
import io.github.arbiter.agent.spi.invocation.InvocationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe, process-local memory store with cosine or lexical retrieval.
 */
public class InMemoryMemoryStore implements MemoryStore {

    private final List<MemoryRecord> records = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void upsert(MemoryRecord record, InvocationContext context) {
        if (record == null) {
            throw new IllegalArgumentException("Memory record is required");
        }
        lock.writeLock().lock();
        try {
            records.removeIf(existing -> existing.namespace().equals(record.namespace())
                    && existing.id().equals(record.id()));
            records.add(record);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<MemoryMatch> search(MemoryQuery query, InvocationContext context) {
        lock.readLock().lock();
        try {
            Set<String> queryTerms = terms(query.text());
            return records.stream()
                    .filter(record -> record.namespace().equals(query.namespace()))
                    .filter(record -> query.types().contains(record.type()))
                    .map(record -> new ScoredRecord(record, score(record, query, queryTerms)))
                    .sorted(Comparator.comparingDouble(ScoredRecord::score).reversed()
                            .thenComparing(scored -> scored.record().createdAt(), Comparator.reverseOrder()))
                    .limit(query.limit())
                    .map(scored -> new MemoryMatch(
                            scored.record(), scored.score(), Map.of("retriever", "in-memory")))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void delete(String namespace, InvocationContext context) {
        lock.writeLock().lock();
        try {
            records.removeIf(record -> record.namespace().equals(namespace));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(String namespace, String id, InvocationContext context) {
        lock.writeLock().lock();
        try {
            records.removeIf(record -> record.namespace().equals(namespace) && record.id().equals(id));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return records.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    static double score(MemoryRecord record, MemoryQuery query, Set<String> queryTerms) {
        if (query.embedding() != null && record.embedding() != null) {
            return cosine(query.embedding().values(), record.embedding().values());
        }
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        List<String> recordTerms = new ArrayList<>(terms(record.content()));
        if (recordTerms.isEmpty()) {
            return 0.0;
        }
        long matches = queryTerms.stream().filter(recordTerms::contains).count();
        return (double) matches / queryTerms.size();
    }

    private static double cosine(double[] left, double[] right) {
        if (left.length != right.length || left.length == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    static Set<String> terms(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> terms = new HashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_-]+")) {
            if (token.isBlank()) {
                continue;
            }
            terms.add(token);
            if (containsCjk(token) && token.length() > 2) {
                for (int index = 0; index < token.length() - 1; index++) {
                    terms.add(token.substring(index, index + 2));
                }
            }
        }
        return Collections.unmodifiableSet(terms);
    }

    private static boolean containsCjk(String token) {
        return token.codePoints().anyMatch(codePoint ->
                codePoint >= 0x3400 && codePoint <= 0x9FFF);
    }

    private record ScoredRecord(MemoryRecord record, double score) {
    }
}
