package io.github.arbiter.agent.spi.tool;

import io.github.arbiter.agent.spi.invocation.InvocationContext;

import java.time.Duration;
import java.util.Map;

public record ToolExecutionContext(
        InvocationContext invocation,
        Map<String, Object> attributes,
        Duration timeout
) {
    public ToolExecutionContext {
        if (invocation == null) {
            throw new IllegalArgumentException("Invocation context is required");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Tool timeout must be positive");
        }
    }

    public static ToolExecutionContext of(String taskId, String sessionId) {
        return new ToolExecutionContext(
                InvocationContext.of(taskId, sessionId, null),
                Map.of(),
                Duration.ofSeconds(30));
    }

    public void checkCancelled() {
        invocation.checkActive();
    }

    public String taskId() { return invocation.taskId(); }
    public String sessionId() { return invocation.sessionId(); }
    public boolean isCancellationRequested() { return invocation.isCancellationRequested(); }
}
