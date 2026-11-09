package io.github.arbiter.agent.observability;

import io.github.arbiter.agent.core.AgentEvent;
import io.github.arbiter.agent.core.AgentEventType;
import io.github.arbiter.agent.core.EventSink;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class AgentMetrics implements EventSink {

    private final Map<AgentEventType, LongAdder> eventCounts = new EnumMap<>(AgentEventType.class);
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final AtomicLong totalInputTokens = new AtomicLong();
    private final AtomicLong totalOutputTokens = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    public AgentMetrics() {
        for (AgentEventType type : AgentEventType.values()) {
            eventCounts.put(type, new LongAdder());
        }
    }

    @Override
    public synchronized void emit(AgentEvent event) {
        eventCounts.get(event.type()).increment();
        switch (event.type()) {
            case MODEL_RESPONSE -> {
                totalInputTokens.addAndGet(number(event, "inputTokens"));
                totalOutputTokens.addAndGet(number(event, "outputTokens"));
            }
            case RUN_FAILED, BUDGET_EXCEEDED -> failures.incrementAndGet();
            default -> {
            }
        }
    }

    public Snapshot snapshot() {
        Map<AgentEventType, Long> counts = new EnumMap<>(AgentEventType.class);
        eventCounts.forEach((type, value) -> counts.put(type, value.sum()));
        Map<String, Long> counterSnapshot = new ConcurrentHashMap<>();
        counters.forEach((name, value) -> counterSnapshot.put(name, value.sum()));
        return new Snapshot(
                Map.copyOf(counts),
                Map.copyOf(counterSnapshot),
                totalInputTokens.get(),
                totalOutputTokens.get(),
                failures.get());
    }

    public void increment(String name) {
        counters.computeIfAbsent(name, ignored -> new LongAdder()).increment();
    }

    private static long number(AgentEvent event, String key) {
        Object value = event.attributes().get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public record Snapshot(
            Map<AgentEventType, Long> eventCounts,
            Map<String, Long> counters,
            long totalInputTokens,
            long totalOutputTokens,
            long failures
    ) {
    }
}
