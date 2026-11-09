package io.github.arbiter.agent.observability;

import io.github.arbiter.agent.core.AgentEvent;
import io.github.arbiter.agent.core.EventSink;

import java.util.List;

public interface EventStore extends EventSink {

    List<AgentEvent> events();

    List<AgentEvent> events(String traceId);

    default void clear() {
        throw new UnsupportedOperationException("Event store is read-only");
    }
}
