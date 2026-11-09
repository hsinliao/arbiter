package io.github.arbiter.agent.spi.model;

import java.time.Duration;
import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters,
        Map<String, Object> outputSchema,
        String version,
        ToolRisk risk,
        boolean idempotent,
        boolean requiresApproval,
        Duration defaultTimeout
) {
    public ToolDefinition {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
        version = version == null || version.isBlank() ? "1.0.0" : version;
        risk = risk == null ? ToolRisk.LOW : risk;
        defaultTimeout = defaultTimeout == null ? Duration.ofSeconds(30) : defaultTimeout;
        if (defaultTimeout.isNegative() || defaultTimeout.isZero()) {
            throw new IllegalArgumentException("defaultTimeout must be positive");
        }
    }

    public static ToolDefinitionBuilder builder() {
        return new ToolDefinitionBuilder();
    }

    public static class ToolDefinitionBuilder {
        private String name;
        private String description;
        private Map<String, Object> parameters = Map.of();
        private Map<String, Object> outputSchema = Map.of();
        private String version = "1.0.0";
        private ToolRisk risk = ToolRisk.LOW;
        private boolean idempotent;
        private boolean requiresApproval;
        private Duration defaultTimeout = Duration.ofSeconds(30);

        public ToolDefinitionBuilder name(String name) { this.name = name; return this; }
        public ToolDefinitionBuilder description(String description) { this.description = description; return this; }
        public ToolDefinitionBuilder parameters(Map<String, Object> parameters) {
            this.parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
            return this;
        }
        public ToolDefinitionBuilder outputSchema(Map<String, Object> outputSchema) {
            this.outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
            return this;
        }
        public ToolDefinitionBuilder version(String version) { this.version = version; return this; }
        public ToolDefinitionBuilder risk(ToolRisk risk) { this.risk = risk; return this; }
        public ToolDefinitionBuilder idempotent(boolean idempotent) { this.idempotent = idempotent; return this; }
        public ToolDefinitionBuilder requiresApproval(boolean requiresApproval) {
            this.requiresApproval = requiresApproval;
            return this;
        }
        public ToolDefinitionBuilder defaultTimeout(Duration defaultTimeout) {
            this.defaultTimeout = defaultTimeout;
            return this;
        }
        public ToolDefinition build() {
            return new ToolDefinition(
                    name, description, parameters, outputSchema, version, risk,
                    idempotent, requiresApproval, defaultTimeout);
        }
    }

}
