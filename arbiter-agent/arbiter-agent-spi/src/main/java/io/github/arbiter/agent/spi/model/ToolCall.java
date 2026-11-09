package io.github.arbiter.agent.spi.model;

import java.util.Map;

public record ToolCall(String id, String name, Map<String, Object> arguments) {

    public ToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    public static ToolCallBuilder builder() {
        return new ToolCallBuilder();
    }

    public static class ToolCallBuilder {
        private String id;
        private String name;
        private Map<String, Object> arguments = Map.of();

        public ToolCallBuilder id(String id) { this.id = id; return this; }
        public ToolCallBuilder name(String name) { this.name = name; return this; }
        public ToolCallBuilder arguments(Map<String, Object> arguments) {
            this.arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
            return this;
        }
        public ToolCall build() {
            return new ToolCall(id, name, arguments);
        }
    }

}
