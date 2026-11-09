package io.github.arbiter.agent.runtime;

import io.github.arbiter.agent.core.AgentConfig;
import io.github.arbiter.agent.core.BudgetExceededException;
import io.github.arbiter.agent.spi.model.Usage;

import java.math.BigDecimal;
import java.math.MathContext;

public final class RunBudget {

    private final AgentConfig config;
    private long inputTokens;
    private long outputTokens;
    private BigDecimal cost = BigDecimal.ZERO;

    public RunBudget(AgentConfig config) {
        this.config = config;
    }

    public synchronized void restore(long inputTokens, long outputTokens, BigDecimal cost) {
        this.inputTokens = Math.max(0, inputTokens);
        this.outputTokens = Math.max(0, outputTokens);
        this.cost = cost == null ? BigDecimal.ZERO : cost;
    }

    public synchronized void record(Usage usage, ModelPricing pricing) {
        int input = usage == null || usage.inputTokens() == null
                ? 0 : Math.max(0, usage.inputTokens());
        int output = usage == null || usage.outputTokens() == null
                ? 0 : Math.max(0, usage.outputTokens());
        inputTokens += input;
        outputTokens += output;
        if (pricing != null) {
            cost = cost
                    .add(pricing.inputCostPerMillionTokens()
                            .multiply(BigDecimal.valueOf(input))
                            .divide(BigDecimal.valueOf(1_000_000), MathContext.DECIMAL64))
                    .add(pricing.outputCostPerMillionTokens()
                            .multiply(BigDecimal.valueOf(output))
                            .divide(BigDecimal.valueOf(1_000_000), MathContext.DECIMAL64));
        }
    }

    public synchronized void ensureWithinLimits() {
        if (config.maxTotalTokens() > 0 && totalTokens() > config.maxTotalTokens()) {
            throw new BudgetExceededException(
                    "Token budget exceeded: " + totalTokens() + " > " + config.maxTotalTokens());
        }
        if (config.maxCost().signum() > 0 && cost.compareTo(config.maxCost()) > 0) {
            throw new BudgetExceededException(
                    "Cost budget exceeded: " + cost + " > " + config.maxCost());
        }
    }

    public synchronized long inputTokens() {
        return inputTokens;
    }

    public synchronized long outputTokens() {
        return outputTokens;
    }

    public synchronized long totalTokens() {
        return inputTokens + outputTokens;
    }

    public synchronized BigDecimal cost() {
        return cost;
    }
}
