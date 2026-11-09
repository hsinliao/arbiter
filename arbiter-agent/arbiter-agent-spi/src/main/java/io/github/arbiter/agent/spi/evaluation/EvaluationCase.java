package io.github.arbiter.agent.spi.evaluation;

import java.util.Map;

public record EvaluationCase(
        String id,
        String input,
        String expectedOutput,
        Map<String, Object> metadata
) {
    public EvaluationCase {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Evaluation id is required");
        }
        input = input == null ? "" : input;
        expectedOutput = expectedOutput == null ? "" : expectedOutput;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
