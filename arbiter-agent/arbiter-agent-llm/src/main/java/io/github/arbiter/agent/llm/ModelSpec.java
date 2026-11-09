package io.github.arbiter.agent.llm;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

public record ModelSpec(
        String code,
        String provider,
        int priority,
        BigDecimal inputCostPerMillionTokens,
        BigDecimal outputCostPerMillionTokens,
        Duration expectedLatency,
        int maxContextTokens,
        Set<String> capabilities,
        boolean available
) {
    public ModelSpec {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Model code is required");
        }
        provider = provider == null ? "" : provider;
        inputCostPerMillionTokens = inputCostPerMillionTokens == null
                ? BigDecimal.ZERO : inputCostPerMillionTokens;
        outputCostPerMillionTokens = outputCostPerMillionTokens == null
                ? BigDecimal.ZERO : outputCostPerMillionTokens;
        if (inputCostPerMillionTokens.signum() < 0 || outputCostPerMillionTokens.signum() < 0) {
            throw new IllegalArgumentException("Model costs must not be negative");
        }
        expectedLatency = expectedLatency == null ? Duration.ofSeconds(2) : expectedLatency;
        if (expectedLatency.isNegative()) {
            throw new IllegalArgumentException("Expected latency must not be negative");
        }
        maxContextTokens = maxContextTokens <= 0 ? 8192 : maxContextTokens;
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    public static Builder builder(String code) {
        return new Builder(code);
    }

    public static final class Builder {
        private final String code;
        private String provider = "";
        private int priority;
        private BigDecimal inputCost = BigDecimal.ZERO;
        private BigDecimal outputCost = BigDecimal.ZERO;
        private Duration latency = Duration.ofSeconds(2);
        private int maxContextTokens = 8192;
        private Set<String> capabilities = Set.of();
        private boolean available = true;

        private Builder(String code) { this.code = code; }
        public Builder provider(String provider) { this.provider = provider; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder inputCost(BigDecimal inputCost) { this.inputCost = inputCost; return this; }
        public Builder outputCost(BigDecimal outputCost) { this.outputCost = outputCost; return this; }
        public Builder expectedLatency(Duration latency) { this.latency = latency; return this; }
        public Builder maxContextTokens(int maxContextTokens) { this.maxContextTokens = maxContextTokens; return this; }
        public Builder capabilities(Set<String> capabilities) { this.capabilities = capabilities; return this; }
        public Builder available(boolean available) { this.available = available; return this; }

        public ModelSpec build() {
            return new ModelSpec(code, provider, priority, inputCost, outputCost,
                    latency, maxContextTokens, capabilities, available);
        }
    }
}
