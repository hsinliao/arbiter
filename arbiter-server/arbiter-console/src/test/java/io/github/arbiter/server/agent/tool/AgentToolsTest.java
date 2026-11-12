package io.github.arbiter.server.agent.tool;

import io.github.arbiter.agent.spi.tool.ToolExecutionContext;
import io.github.arbiter.agent.spi.tool.ToolResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolsTest {

    @Test
    void recentErrorsAreLimitedAndRedacted(@TempDir Path directory) throws Exception {
        Path log = directory.resolve("console.log");
        Files.writeString(log, """
                2026-11-12 INFO started
                2026-11-12 ERROR apiKey=super-secret failed
                2026-11-12 WARN Authorization: Bearer abc.def
                """);
        RecentErrorsTool tool = new RecentErrorsTool(log);

        ToolResult result = tool.execute(
                Map.of("limit", 2), ToolExecutionContext.of("task", "session"));

        assertTrue(result.success());
        assertTrue(result.content().contains("apiKey=***"));
        assertFalse(result.content().contains("super-secret"));
        assertFalse(result.content().contains("Bearer abc.def"));
    }

    @Test
    void bundledRunbookCanBeSearched() {
        RunbookSearchTool tool = new RunbookSearchTool();

        ToolResult result = tool.execute(
                Map.of("query", "initial administrator"),
                ToolExecutionContext.of("task", "session"));

        assertTrue(result.success());
        assertTrue(result.content().contains("initial-admin.txt"));
    }
}
