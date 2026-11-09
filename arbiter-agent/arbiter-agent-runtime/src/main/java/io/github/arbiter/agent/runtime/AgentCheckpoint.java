package io.github.arbiter.agent.runtime;

import io.github.arbiter.agent.core.AgentState;
import io.github.arbiter.agent.core.AgentTask;
import io.github.arbiter.agent.spi.model.ModelMessage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AgentCheckpoint(
        AgentTask task,
        String sessionId,
        String traceId,
        AgentState state,
        List<ModelMessage> messages,
        Map<String, Object> variables,
        int iterations,
        long inputTokens,
        long outputTokens,
        BigDecimal cost,
        String output,
        String failure,
        long eventSequence,
        Instant startedAt,
        Instant updatedAt
) {
    public AgentCheckpoint {
        if (task == null || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Task and session are required");
        }
        traceId = traceId == null ? "" : traceId;
        state = state == null ? AgentState.CREATED : state;
        messages = messages == null ? List.of() : List.copyOf(messages);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        cost = cost == null ? BigDecimal.ZERO : cost;
        output = output == null ? "" : output;
        failure = failure == null ? "" : failure;
        startedAt = startedAt == null ? Instant.now() : startedAt;
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    public String key() {
        return task.id().length() + ":" + task.id() + sessionId;
    }

}
