package io.github.arbiter.agent.spi.evaluation;

import io.github.arbiter.agent.spi.invocation.InvocationContext;

import java.util.Map;

public record EvaluationContext(
        String actualOutput,
        Map<String, Object> evidence,
        InvocationContext invocation
) {
    public EvaluationContext {
        actualOutput = actualOutput == null ? "" : actualOutput;
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        if (invocation == null) {
            throw new IllegalArgumentException("Invocation context is required");
        }
    }
}
