package io.github.arbiter.server.agent.tool;

import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;
import io.github.arbiter.agent.spi.tool.AgentTool;
import io.github.arbiter.agent.spi.tool.ToolExecutionContext;
import io.github.arbiter.agent.spi.tool.ToolResult;
import io.github.arbiter.api.dto.QueryResponse;
import io.github.arbiter.server.service.QueryService;

import java.util.List;
import java.util.Map;

/** Exposes the existing sample resource query as a read-only Agent tool. */
public final class QueryResourceTool implements AgentTool {
    private final QueryService queryService;

    public QueryResourceTool(QueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("query_resource")
                .description("Looks up one Arbiter sample resource by name.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "name", Map.of(
                                        "type", "string",
                                        "description", "Resource name, 1-100 characters")),
                        "required", List.of("name")))
                .idempotent(true)
                .build();
    }

    @Override
    public String group() {
        return "reader";
    }

    @Override
    public ToolResult execute(ToolCall call, ToolExecutionContext context) {
        context.checkCancelled();
        Object rawName = call.arguments().get("name");
        if (rawName == null) {
            return ToolResult.failure("Missing required argument: name");
        }
        try {
            QueryResponse response = queryService.query(rawName.toString());
            return ToolResult.success(
                    "name=" + response.name() + "\nvalue=" + response.value(),
                    Map.of("name", response.name(), "value", response.value()));
        } catch (RuntimeException invalid) {
            return ToolResult.failure("Resource query failed: " + invalid.getMessage());
        }
    }
}
