package io.github.arbiter.agent.spi.tool;

import java.util.Map;

public record ToolError(String code, String message, boolean retryable, Map<String, Object> details) {
    public ToolError {
        code = code == null || code.isBlank() ? "TOOL_ERROR" : code;
        message = message == null ? "" : message;
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
