package io.github.arbiter.agent.spi.model;

import java.util.List;
import java.util.Map;

public record ModelMessage(
        MessageRole role,
        String content,
        String toolCallId,
        String toolName,
        List<ToolCall> toolCalls,
        Map<String, Object> metadata
) {
    public ModelMessage {
        if (role == null) {
            throw new IllegalArgumentException("Message role is required");
        }
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ModelMessage system(String content) {
        return new ModelMessage(MessageRole.SYSTEM, content, null, null, List.of(), Map.of());
    }

    public static ModelMessage user(String content) {
        return new ModelMessage(MessageRole.USER, content, null, null, List.of(), Map.of());
    }

    public static ModelMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ModelMessage(MessageRole.ASSISTANT, content, null, null, toolCalls, Map.of());
    }

    public static ModelMessage tool(String toolCallId, String toolName, String content) {
        return new ModelMessage(MessageRole.TOOL, content, toolCallId, toolName, List.of(), Map.of());
    }
}
