package io.github.arbiter.agent.spi.model;

import java.util.List;
import java.util.Map;

public record ModelResponse(
        String content,
        String modelId,
        List<ToolCall> toolCalls,
        Usage usage,
        ModelFinishReason finishReason,
        Map<String, Object> providerMetadata
) {
    public ModelResponse {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        finishReason = finishReason == null ? ModelFinishReason.UNKNOWN : finishReason;
        providerMetadata = providerMetadata == null ? Map.of() : Map.copyOf(providerMetadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String content;
        private String modelId;
        private List<ToolCall> toolCalls = List.of();
        private Usage usage;
        private ModelFinishReason finishReason = ModelFinishReason.UNKNOWN;
        private Map<String, Object> providerMetadata = Map.of();

        public Builder content(String content) { this.content = content; return this; }
        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder toolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; return this; }
        public Builder usage(Usage usage) { this.usage = usage; return this; }
        public Builder finishReason(ModelFinishReason finishReason) {
            this.finishReason = finishReason;
            return this;
        }
        public Builder providerMetadata(Map<String, Object> providerMetadata) {
            this.providerMetadata = providerMetadata;
            return this;
        }

        public ModelResponse build() {
            return new ModelResponse(content, modelId, toolCalls, usage, finishReason, providerMetadata);
        }
    }

    public ModelResponse withModelId(String newModelId) {
        return new ModelResponse(content, newModelId, toolCalls, usage, finishReason, providerMetadata);
    }
}
