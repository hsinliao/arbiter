package io.github.arbiter.agent.observability;

import io.github.arbiter.agent.core.AgentEvent;
import io.github.arbiter.agent.core.AgentEventType;
import io.github.arbiter.agent.core.EventSink;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class EventReplayer {

    private final EventStore store;
    private final Duration maxReplayDelay;

    public EventReplayer(EventStore store) {
        this(store, Duration.ofSeconds(5));
    }

    public EventReplayer(EventStore store, Duration maxReplayDelay) {
        this.store = Objects.requireNonNull(store, "store");
        if (maxReplayDelay == null || maxReplayDelay.isNegative() || maxReplayDelay.isZero()) {
            throw new IllegalArgumentException("maxReplayDelay must be positive");
        }
        this.maxReplayDelay = maxReplayDelay;
    }

    public ReplayResult replay(String traceId, EventSink sink) {
        return replay(event -> event.traceId().equals(traceId), sink, false);
    }

    public ReplayResult replay(String traceId, EventSink sink, boolean preserveTiming) {
        return replay(event -> event.traceId().equals(traceId), sink, preserveTiming);
    }

    public ReplayResult replay(Predicate<AgentEvent> filter, EventSink sink, boolean preserveTiming) {
        List<AgentEvent> events = store.events().stream().filter(filter).toList();
        Instant previous = null;
        int replayed = 0;
        AgentEvent last = null;
        for (AgentEvent event : events) {
            if (preserveTiming && previous != null) {
                Duration delay = Duration.between(previous, event.occurredAt());
                if (!sleep(delay.compareTo(maxReplayDelay) > 0 ? maxReplayDelay : delay)) {
                    break;
                }
            }
            sink.emit(event);
            replayed++;
            last = event;
            previous = event.occurredAt();
        }
        return new ReplayResult(replayed, last == null ? null : last.type());
    }

    private static boolean sleep(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return true;
        }
        try {
            Thread.sleep(duration.toMillis());
            return true;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public record ReplayResult(int eventCount, AgentEventType finalEvent) {
    }
}
