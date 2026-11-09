package io.github.arbiter.agent.spi.evaluation;

@FunctionalInterface
public interface Evaluator {

    EvaluationResult evaluate(EvaluationCase evalCase, EvaluationContext context);
}
