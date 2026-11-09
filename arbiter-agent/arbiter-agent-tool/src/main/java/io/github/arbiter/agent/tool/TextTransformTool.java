package io.github.arbiter.agent.tool;

import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;
import io.github.arbiter.agent.spi.tool.AgentTool;
import io.github.arbiter.agent.spi.tool.ToolExecutionContext;
import io.github.arbiter.agent.spi.tool.ToolResult;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TextTransformTool implements AgentTool {

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("transform_text")
                .description("Transforms text by applying upper, lower or strip. Use for deterministic text normalization.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "text", Map.of("type", "string", "description", "Text to transform"),
                                "operation", Map.of(
                                        "type", "string",
                                        "enum", List.of("upper", "lower", "strip"),
                                        "description", "Transformation to apply")),
                        "required", List.of("text", "operation")))
                .idempotent(true)
                .build();
    }

    @Override
    public String group() {
        return "text";
    }

    @Override
    public ToolResult execute(ToolCall call, ToolExecutionContext context) {
        Map<String, Object> args = call.arguments();
        String text = String.valueOf(args.getOrDefault("text", ""));
        String operation = String.valueOf(args.getOrDefault("operation", ""));
        return switch (operation) {
            case "upper" -> ToolResult.success(text.toUpperCase(Locale.ROOT));
            case "lower" -> ToolResult.success(text.toLowerCase(Locale.ROOT));
            case "strip" -> ToolResult.success(text.strip());
            default -> ToolResult.failure("Unsupported operation: " + operation);
        };
    }
}
