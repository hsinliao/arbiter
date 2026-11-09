package io.github.arbiter.agent.spi.model;

public record ToolCallDelta(
        int index,
        String id,
        String name,
        String argumentsFragment
) {
    public ToolCallDelta {
        if (index < 0) {
            throw new IllegalArgumentException("Tool call index must not be negative");
        }
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        argumentsFragment = argumentsFragment == null ? "" : argumentsFragment;
    }
}
