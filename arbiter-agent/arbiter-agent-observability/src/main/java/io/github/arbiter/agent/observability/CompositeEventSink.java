package io.github.arbiter.agent.observability;

import io.github.arbiter.agent.core.AgentEvent;
import io.github.arbiter.agent.core.EventSink;

import java.util.List;

public final class CompositeEventSink implements EventSink {

    private final List<EventSink> sinks;

    public CompositeEventSink(EventSink... sinks) {
        this.sinks = List.of(sinks);
    }

    @Override
    public void emit(AgentEvent event) {
        RuntimeException failure = null;
        for (EventSink sink : sinks) {
            try {
                sink.emit(event);
            } catch (RuntimeException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
