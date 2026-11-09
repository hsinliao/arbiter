package io.github.arbiter.agent.core;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

public record AgentConfig(
        String name,
        String systemPrompt,
        String modelCode,
        int maxIterations,
        int maxTotalTokens,
        BigDecimal maxCost,
        Duration timeout,
        Set<String> toolGroups,
        Double temperature,
        Integer maxOutputTokens
) {
    public AgentConfig {
        name = name == null || name.isBlank() ? "arbiter-agent" : name;
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be positive");
        }
        if (maxTotalTokens < 0) {
            throw new IllegalArgumentException("maxTotalTokens must not be negative");
        }
        maxCost = maxCost == null ? BigDecimal.ZERO : maxCost;
        if (maxCost.signum() < 0) {
            throw new IllegalArgumentException("maxCost must not be negative");
        }
        timeout = timeout == null ? Duration.ofMinutes(2) : timeout;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        toolGroups = toolGroups == null ? Set.of("default") : Set.copyOf(toolGroups);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name = "arbiter-agent";
        private String systemPrompt = "";
        private String modelCode;
        private int maxIterations = 8;
        private int maxTotalTokens;
        private BigDecimal maxCost = BigDecimal.ZERO;
        private Duration timeout = Duration.ofMinutes(2);
        private Set<String> toolGroups = Set.of("default");
        private Double temperature;
        private Integer maxOutputTokens;

        public Builder name(String name) { this.name = name; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder modelCode(String modelCode) { this.modelCode = modelCode; return this; }
        public Builder maxIterations(int maxIterations) { this.maxIterations = maxIterations; return this; }
        public Builder maxTotalTokens(int maxTotalTokens) { this.maxTotalTokens = maxTotalTokens; return this; }
        public Builder maxCost(BigDecimal maxCost) { this.maxCost = maxCost; return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }
        public Builder toolGroups(Set<String> toolGroups) { this.toolGroups = toolGroups; return this; }
        public Builder temperature(Double temperature) { this.temperature = temperature; return this; }
        public Builder maxOutputTokens(Integer maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; return this; }

        public AgentConfig build() {
            return new AgentConfig(
                    name, systemPrompt, modelCode, maxIterations, maxTotalTokens, maxCost,
                    timeout, toolGroups, temperature, maxOutputTokens);
        }
    }
}
