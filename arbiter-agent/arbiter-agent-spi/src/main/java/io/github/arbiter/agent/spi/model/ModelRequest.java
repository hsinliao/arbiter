package io.github.arbiter.agent.spi.model;

import java.util.List;
import java.util.Map;

public record ModelRequest(
        String modelId,
        List<ModelMessage> messages,
        SamplingOptions sampling,
        List<ToolDefinition> tools,
        Map<String, Object> metadata
) {
    public ModelRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("At least one message is required");
        }
        sampling = sampling == null ? SamplingOptions.empty() : sampling;
        tools = tools == null ? List.of() : List.copyOf(tools);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String modelId;
        private List<ModelMessage> messages = List.of();
        private SamplingOptions sampling = SamplingOptions.empty();
        private List<ToolDefinition> tools = List.of();
        private Map<String, Object> metadata = Map.of();

        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder messages(List<ModelMessage> messages) { this.messages = messages; return this; }
        public Builder sampling(SamplingOptions sampling) { this.sampling = sampling; return this; }
        public Builder tools(List<ToolDefinition> tools) { this.tools = tools; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public ModelRequest build() {
            return new ModelRequest(modelId, messages, sampling, tools, metadata);
        }
    }
}
