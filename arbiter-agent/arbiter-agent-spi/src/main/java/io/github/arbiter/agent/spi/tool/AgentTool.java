package io.github.arbiter.agent.spi.tool;

import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;

import java.util.Map;

/**
 * A model-callable tool. Implementations should be side-effect aware and idempotent where possible.
 */
public interface AgentTool {

    ToolDefinition definition();

    default String group() {
        return "default";
    }

    ToolResult execute(ToolCall call, ToolExecutionContext context);

    default ToolResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        return execute(ToolCall.builder()
                .name(definition().name())
                .arguments(arguments)
                .build(), context);
    }
}
