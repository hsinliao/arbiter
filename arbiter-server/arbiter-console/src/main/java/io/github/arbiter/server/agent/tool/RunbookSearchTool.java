package io.github.arbiter.server.agent.tool;

import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;
import io.github.arbiter.agent.spi.tool.AgentTool;
import io.github.arbiter.agent.spi.tool.ToolExecutionContext;
import io.github.arbiter.agent.spi.tool.ToolResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Searches the bundled read-only Console assistant runbook. */
public final class RunbookSearchTool implements AgentTool {
    private static final int MAX_RESULTS = 8;
    private final List<String> lines;

    public RunbookSearchTool() {
        this.lines = loadRunbook();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("search_runbook")
                .description("Searches Arbiter console and operations guidance bundled with the server.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "Keywords to search for")),
                        "required", List.of("query")))
                .idempotent(true)
                .build();
    }

    @Override
    public String group() {
        return "docs";
    }

    @Override
    public ToolResult execute(ToolCall call, ToolExecutionContext context) {
        context.checkCancelled();
        Object rawQuery = call.arguments().get("query");
        String query = rawQuery == null ? "" : rawQuery.toString().strip();
        if (query.isEmpty()) {
            return ToolResult.failure("Missing required argument: query");
        }
        Set<String> terms = new LinkedHashSet<>(
                Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+"))
                        .filter(value -> value.length() > 1)
                        .toList());
        if (terms.isEmpty()) {
            return ToolResult.failure("Query must contain at least one searchable term");
        }
        List<ScoredLine> matches = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String lower = line.toLowerCase(Locale.ROOT);
            int score = (int) terms.stream().filter(lower::contains).count();
            if (score > 0) {
                matches.add(new ScoredLine(index + 1, score, line));
            }
        }
        List<String> result = matches.stream()
                .sorted(Comparator.comparingInt(ScoredLine::score).reversed()
                        .thenComparingInt(ScoredLine::lineNumber))
                .limit(MAX_RESULTS)
                .map(line -> "L" + line.lineNumber() + ": " + line.text())
                .toList();
        return ToolResult.success(
                result.isEmpty() ? "No runbook entries matched: " + query
                        : String.join("\n", result),
                Map.of("count", result.size()));
    }

    private static List<String> loadRunbook() {
        try (InputStream input = RunbookSearchTool.class
                .getResourceAsStream("/runbooks/console-assistant.md")) {
            if (input == null) {
                return List.of("Console assistant runbook is not packaged.");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.isBlank())
                    .toList();
        } catch (IOException failure) {
            return List.of("Console assistant runbook could not be read.");
        }
    }

    private record ScoredLine(int lineNumber, int score, String text) {
    }
}
