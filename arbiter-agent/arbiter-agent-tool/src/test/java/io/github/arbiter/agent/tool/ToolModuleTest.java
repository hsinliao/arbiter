package io.github.arbiter.agent.tool;

import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.invocation.RoutingRequirements;
import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;
import io.github.arbiter.agent.spi.tool.AgentTool;
import io.github.arbiter.agent.spi.tool.ToolExecutionContext;
import io.github.arbiter.agent.spi.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolModuleTest {

    @Test
    void filtersToolsByGroup() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ClockTool());
        registry.register(new TextTransformTool());

        assertTrue(registry.definitions(Set.of("system")).stream()
                .anyMatch(definition -> definition.name().equals("current_time")));
        assertFalse(registry.definitions(Set.of("text")).stream()
                .anyMatch(definition -> definition.name().equals("current_time")));
    }

    @Test
    void timesOutSlowTool() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new SlowTool());
        try (DefaultToolExecutor executor = new DefaultToolExecutor(registry)) {
            ToolResult result = executor.execute(
                    ToolCall.builder().name("slow").arguments(Map.of()).build(),
                    new ToolExecutionContext(
                            InvocationContext.of("task", "session", null),
                            Map.of(),
                            Duration.ofMillis(20)));
            assertFalse(result.success());
            assertTrue(result.content().contains("timed out"));
            assertEquals("TIMEOUT", result.error().code());
        }
    }

    @Test
    void returnsFailureForUnknownTool() throws Exception {
        try (DefaultToolExecutor executor = new DefaultToolExecutor(new ToolRegistry())) {
            ToolResult result = executor.execute(
                    ToolCall.builder().name("missing").arguments(Map.of()).build(),
                    ToolExecutionContext.of("task", "session"));
            assertFalse(result.success());
            assertTrue(result.content().contains("Unknown tool"));
        }
    }

    @Test
    void cancelsRunningTool() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new SlowTool());
        AtomicBoolean cancelled = new AtomicBoolean();
        Thread canceller = new Thread(() -> {
            try {
                Thread.sleep(20);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            cancelled.set(true);
        });
        canceller.start();
        try (DefaultToolExecutor executor = new DefaultToolExecutor(registry)) {
            ToolResult result = executor.execute(
                    ToolCall.builder().name("slow").arguments(Map.of()).build(),
                    new ToolExecutionContext(
                            new InvocationContext(
                                    null, "trace", "task", "session", null,
                                    cancelled::get,
                                    RoutingRequirements.unlimited(null),
                                    Map.of()),
                            Map.of(),
                            Duration.ofSeconds(2)));
            assertFalse(result.success());
            assertTrue(result.content().contains("cancelled"));
        } finally {
            canceller.join(1_000);
        }
    }

    private static final class SlowTool implements AgentTool {
        @Override
        public ToolDefinition definition() {
            return ToolDefinition.builder()
                    .name("slow")
                    .description("Sleeps beyond the configured timeout for executor tests.")
                    .parameters(Map.of("type", "object"))
                    .build();
        }

        @Override
        public ToolResult execute(ToolCall call, ToolExecutionContext context) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            return ToolResult.success("finished");
        }
    }
}
