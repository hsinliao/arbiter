package io.github.arbiter.agent.core;

@FunctionalInterface
public interface EventSink {

    EventSink NOOP = event -> {
    };

    void emit(AgentEvent event);

    default EventSink andThen(EventSink next) {
        return event -> {
            emit(event);
            next.emit(event);
        };
    }
}
