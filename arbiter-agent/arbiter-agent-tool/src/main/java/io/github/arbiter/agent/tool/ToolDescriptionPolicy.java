package io.github.arbiter.agent.tool;

import io.github.arbiter.agent.spi.model.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

public final class ToolDescriptionPolicy {

    private final int minimumDescriptionLength;

    public ToolDescriptionPolicy() {
        this(12);
    }

    public ToolDescriptionPolicy(int minimumDescriptionLength) {
        this.minimumDescriptionLength = Math.max(1, minimumDescriptionLength);
    }

    public List<String> validate(ToolDefinition definition) {
        List<String> issues = new ArrayList<>();
        if (definition == null) {
            issues.add("definition is required");
            return issues;
        }
        if (definition.name() == null || !definition.name().matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            issues.add("name must match [A-Za-z][A-Za-z0-9_-]{0,63}");
        }
        if (definition.description() == null
                || definition.description().strip().length() < minimumDescriptionLength) {
            issues.add("description is too short; model needs a clear usage statement");
        }
        if (definition.parameters() == null || !"object".equals(definition.parameters().get("type"))) {
            issues.add("parameters must be a JSON-schema object");
        }
        return List.copyOf(issues);
    }

    public void requireValid(ToolDefinition definition) {
        List<String> issues = validate(definition);
        if (!issues.isEmpty()) {
            throw new IllegalArgumentException("Invalid tool definition: " + String.join(", ", issues));
        }
    }
}
