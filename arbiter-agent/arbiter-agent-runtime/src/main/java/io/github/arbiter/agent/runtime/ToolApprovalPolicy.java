package io.github.arbiter.agent.runtime;

import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;

@FunctionalInterface
public interface ToolApprovalPolicy {

    boolean approve(ToolDefinition definition, ToolCall call, InvocationContext context);
}
