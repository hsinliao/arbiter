package io.github.arbiter.agent.observability;

import io.github.arbiter.agent.core.AgentEvent;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record TraceTimeline(String traceId, List<AgentEvent> events, Duration duration) {

    public TraceTimeline {
        events = List.copyOf(events);
    }

    public static TraceTimeline from(EventStore store, String traceId) {
        List<AgentEvent> events = store.events(traceId).stream()
                .sorted(Comparator.comparing(AgentEvent::occurredAt)
                        .thenComparingLong(AgentEvent::sequence))
                .toList();
        Duration duration = events.isEmpty()
                ? Duration.ZERO
                : Duration.between(events.get(0).occurredAt(), events.get(events.size() - 1).occurredAt());
        return new TraceTimeline(traceId, events, duration);
    }

    public Map<String, Long> eventCounts() {
        return events.stream().collect(Collectors.groupingBy(
                event -> event.type().name(),
                Collectors.counting()));
    }
}
