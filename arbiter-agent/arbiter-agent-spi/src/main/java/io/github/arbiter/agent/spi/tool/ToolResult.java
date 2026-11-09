package io.github.arbiter.agent.spi.tool;

import java.util.Map;

public record ToolResult(
        boolean success,
        String content,
        Map<String, Object> data,
        ToolError error
) {

    public ToolResult {
        content = content == null ? "" : content;
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static ToolResult success(String content) {
        return new ToolResult(true, content, Map.of(), null);
    }

    public static ToolResult success(String content, Map<String, Object> data) {
        return new ToolResult(true, content, data, null);
    }

    public static ToolResult failure(String content) {
        return failure(content, new ToolError("TOOL_ERROR", content, false, Map.of()));
    }

    public static ToolResult failure(String content, ToolError error) {
        return new ToolResult(false, content, Map.of(), error);
    }
}
