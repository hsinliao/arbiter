package io.github.arbiter.agent.observability;

import io.github.arbiter.agent.core.AgentEvent;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class RecordingEventStore implements EventStore {

    private final AgentEvent[] events;
    private final AtomicLong writes = new AtomicLong();

    public RecordingEventStore() {
        this(10_000);
    }

    public RecordingEventStore(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.events = new AgentEvent[capacity];
    }

    @Override
    public void emit(AgentEvent event) {
        Objects.requireNonNull(event, "event");
        long write = writes.getAndIncrement();
        events[(int) (write % events.length)] = event;
    }

    @Override
    public List<AgentEvent> events() {
        return snapshot();
    }

    @Override
    public List<AgentEvent> events(String traceId) {
        return snapshot().stream()
                .filter(event -> event.traceId().equals(traceId))
                .toList();
    }

    @Override
    public void clear() {
        Arrays.fill(events, null);
        writes.set(0);
    }

    private List<AgentEvent> snapshot() {
        return Arrays.stream(events)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AgentEvent::occurredAt)
                        .thenComparingLong(AgentEvent::sequence))
                .toList();
    }
}
