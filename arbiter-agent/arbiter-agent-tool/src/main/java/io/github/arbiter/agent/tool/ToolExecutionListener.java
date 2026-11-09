package io.github.arbiter.agent.tool;

import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.tool.ToolResult;

import java.time.Duration;

@FunctionalInterface
public interface ToolExecutionListener {

    ToolExecutionListener NOOP = (call, result, duration) -> {
    };

    void onResult(ToolCall call, ToolResult result, Duration duration);
}
