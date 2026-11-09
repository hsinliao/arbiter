package io.github.arbiter.agent.runtime;

import java.math.BigDecimal;

public record ModelPricing(
        String model,
        BigDecimal inputCostPerMillionTokens,
        BigDecimal outputCostPerMillionTokens
) {
    public ModelPricing {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model name is required");
        }
        inputCostPerMillionTokens = inputCostPerMillionTokens == null
                ? BigDecimal.ZERO : inputCostPerMillionTokens;
        outputCostPerMillionTokens = outputCostPerMillionTokens == null
                ? BigDecimal.ZERO : outputCostPerMillionTokens;
        if (inputCostPerMillionTokens.signum() < 0 || outputCostPerMillionTokens.signum() < 0) {
            throw new IllegalArgumentException("Model prices must not be negative");
        }
    }
}
