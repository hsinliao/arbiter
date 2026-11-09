package io.github.arbiter.agent.observability;

import io.github.arbiter.agent.core.AgentEvent;
import io.github.arbiter.agent.core.AgentEventType;
import io.github.arbiter.agent.spi.evaluation.EvaluationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilityTest {

    @Test
    void replaysTraceAndComputesMetrics() {
        RecordingEventStore store = new RecordingEventStore();
        Instant now = Instant.now();
        store.emit(AgentEvent.builder(AgentEventType.RUN_STARTED)
                .traceId("trace-1").sequence(1).occurredAt(now).build());
        store.emit(AgentEvent.builder(AgentEventType.MODEL_RESPONSE)
                .traceId("trace-1").sequence(2).occurredAt(now.plusMillis(2))
                .attributes(Map.of("inputTokens", 5, "outputTokens", 3)).build());
        store.emit(AgentEvent.builder(AgentEventType.RUN_COMPLETED)
                .traceId("trace-1").sequence(3).occurredAt(now.plusMillis(4)).build());

        AgentMetrics metrics = new AgentMetrics();
        EventReplayer replayer = new EventReplayer(store);
        var result = replayer.replay("trace-1", metrics);
        assertTrue(result.eventCount() > 0);
        assertEquals(5, metrics.snapshot().totalInputTokens());
        assertEquals(3, metrics.snapshot().totalOutputTokens());

        EvaluationResult evaluation = new EvaluationHarness().evaluate(
                "trace-1", "question", "answer", store,
                (evalCase, context) -> new EvaluationResult(
                        "smoke", !context.actualOutput().isBlank(), 1.0, "non-empty", context.evidence()));
        assertTrue(evaluation.passed());
    }
}
