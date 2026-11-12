package io.github.arbiter.server.agent.tool;

import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;
import io.github.arbiter.agent.spi.tool.AgentTool;
import io.github.arbiter.agent.spi.tool.ToolExecutionContext;
import io.github.arbiter.agent.spi.tool.ToolResult;
import io.github.arbiter.api.dto.SystemStatusResponse;
import io.github.arbiter.server.service.StatusService;

import java.util.Map;

/** Read-only view of the public service status contract. */
public final class ServiceStatusTool implements AgentTool {
    private final StatusService statusService;

    public ServiceStatusTool(StatusService statusService) {
        this.statusService = statusService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("get_service_status")
                .description("Returns the Arbiter service state, version and server time.")
                .parameters(Map.of("type", "object", "properties", Map.of()))
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
        SystemStatusResponse status = statusService.status();
        return ToolResult.success(
                "state=" + status.state() + "\n"
                        + "version=" + status.version() + "\n"
                        + "serverTime=" + status.serverTime(),
                Map.of(
                        "state", status.state(),
                        "version", status.version(),
                        "serverTime", status.serverTime()));
    }
}
