package io.github.arbiter.agent.core;

import io.github.arbiter.agent.spi.model.Usage;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record AgentResult(
        String taskId,
        String sessionId,
        AgentRunStatus status,
        AgentState state,
        String output,
        int iterations,
        Usage usage,
        BigDecimal cost,
        String failure,
        Instant startedAt,
        Instant finishedAt,
        Map<String, Object> metadata
) {
    public AgentResult {
        output = output == null ? "" : output;
        cost = cost == null ? BigDecimal.ZERO : cost;
        failure = failure == null ? "" : failure;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Duration duration() {
        if (startedAt == null || finishedAt == null) {
            return Duration.ZERO;
        }
        return Duration.between(startedAt, finishedAt);
    }
}
