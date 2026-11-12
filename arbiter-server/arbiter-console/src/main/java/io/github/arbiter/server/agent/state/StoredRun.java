package io.github.arbiter.server.agent.state;

import java.math.BigDecimal;
import java.time.Instant;

public record StoredRun(
        String runId,
        String conversationId,
        String owner,
        String scope,
        String prompt,
        String status,
        String output,
        String failure,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        int iterations,
        int inputTokens,
        int outputTokens,
        BigDecimal cost
) {
}
