package io.github.arbiter.agent.observability;

import io.github.arbiter.agent.core.AgentEvent;
import io.github.arbiter.agent.core.AgentEventType;
import io.github.arbiter.agent.spi.evaluation.EvaluationCase;
import io.github.arbiter.agent.spi.evaluation.EvaluationContext;
import io.github.arbiter.agent.spi.evaluation.EvaluationResult;
import io.github.arbiter.agent.spi.evaluation.Evaluator;
import io.github.arbiter.agent.spi.invocation.InvocationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EvaluationHarness {

    public EvaluationResult evaluate(
            String traceId,
            String input,
            String output,
            EventStore store,
            Evaluator evaluator
    ) {
        List<AgentEvent> events = store.events(traceId);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("traceId", traceId);
        evidence.put("eventCount", events.size());
        events.stream()
                .filter(event -> event.type() == AgentEventType.TOOL_RESULT)
                .forEach(event -> evidence.put(event.eventId(), event.attributes()));
        EvaluationCase evalCase = new EvaluationCase(
                traceId, input, "", Map.of());
        EvaluationContext context = new EvaluationContext(
                output,
                Map.copyOf(evidence),
                InvocationContext.of(traceId, traceId, null));
        return evaluator.evaluate(evalCase, context);
    }
}
