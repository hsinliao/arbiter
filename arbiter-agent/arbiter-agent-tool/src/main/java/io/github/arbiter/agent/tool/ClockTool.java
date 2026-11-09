package io.github.arbiter.agent.tool;

import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;
import io.github.arbiter.agent.spi.tool.AgentTool;
import io.github.arbiter.agent.spi.tool.ToolExecutionContext;
import io.github.arbiter.agent.spi.tool.ToolResult;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ClockTool implements AgentTool {

    private final Clock clock;

    public ClockTool() {
        this(Clock.systemUTC());
    }

    public ClockTool(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("current_time")
                .description("Returns the current date and time for an IANA timezone such as Asia/Shanghai.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "timezone", Map.of(
                                        "type", "string",
                                        "description", "IANA timezone identifier")),
                        "required", List.of()))
                .idempotent(true)
                .build();
    }

    @Override
    public String group() {
        return "system";
    }

    @Override
    public ToolResult execute(ToolCall call, ToolExecutionContext context) {
        Object configured = call.arguments().get("timezone");
        String timezone = configured == null ? "UTC" : configured.toString();
        try {
            String value = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    .format(clock.instant().atZone(ZoneId.of(timezone)));
            return ToolResult.success(value);
        } catch (RuntimeException error) {
            return ToolResult.failure("Invalid timezone: " + timezone);
        }
    }
}
