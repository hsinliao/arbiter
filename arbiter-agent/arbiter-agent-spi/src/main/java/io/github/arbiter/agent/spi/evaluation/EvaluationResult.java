package io.github.arbiter.agent.spi.evaluation;

import java.util.Map;

public record EvaluationResult(
        String evaluator,
        boolean passed,
        double score,
        String explanation,
        Map<String, Object> metrics
) {
    public EvaluationResult {
        score = Math.max(0.0, Math.min(1.0, score));
        explanation = explanation == null ? "" : explanation;
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }
}
