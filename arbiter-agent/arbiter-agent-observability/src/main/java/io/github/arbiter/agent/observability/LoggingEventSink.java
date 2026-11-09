package io.github.arbiter.agent.observability;

import io.github.arbiter.agent.core.AgentEvent;
import io.github.arbiter.agent.core.EventSink;

import java.util.Objects;

public final class LoggingEventSink implements EventSink {

    private final System.Logger logger;

    public LoggingEventSink() {
        this(System.getLogger("io.github.arbiter.agent"));
    }

    public LoggingEventSink(System.Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void emit(AgentEvent event) {
        System.Logger.Level level = switch (event.type()) {
            case RUN_FAILED, BUDGET_EXCEEDED -> System.Logger.Level.ERROR;
            case RUN_CANCELLED -> System.Logger.Level.WARNING;
            case RUN_COMPLETED, RUN_STARTED, RUN_RESUMED -> System.Logger.Level.INFO;
            default -> System.Logger.Level.DEBUG;
        };
        logger.log(level, "agent_event type={0} trace={1} task={2} session={3} attrs={4}",
                event.type(), event.traceId(), event.taskId(), event.sessionId(), event.attributes());
    }
}
