package io.github.arbiter.server.agent.tool;

import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;
import io.github.arbiter.agent.spi.tool.AgentTool;
import io.github.arbiter.agent.spi.tool.ToolExecutionContext;
import io.github.arbiter.agent.spi.tool.ToolResult;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Returns a small, redacted tail of warning and error log lines. It never
 * returns a configurable path and never attempts to follow symlinks.
 */
public final class RecentErrorsTool implements AgentTool {
    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 20;
    private static final int MAX_LINE_CHARS = 600;
    private static final long TAIL_BYTES = 256L * 1024L;
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(token|password|secret|api[_-]?key)\\s*[=:]\\s*[^\\s,;]+");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+\\S+");

    private final Path logFile;

    public RecentErrorsTool(Path logFile) {
        this.logFile = logFile.toAbsolutePath().normalize();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("get_recent_errors")
                .description("Returns a small, redacted tail of recent warning and error log lines.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "limit", Map.of(
                                        "type", "integer",
                                        "minimum", 1,
                                        "maximum", MAX_LIMIT,
                                        "description", "Number of matching lines, default 8"))))
                .idempotent(true)
                .build();
    }

    @Override
    public String group() {
        return "admin";
    }

    @Override
    public ToolResult execute(ToolCall call, ToolExecutionContext context) {
        context.checkCancelled();
        int limit = limit(call.arguments().get("limit"));
        try {
            if (!Files.isRegularFile(logFile, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(logFile)) {
                return ToolResult.success("No readable console log is available.",
                        Map.of("count", 0));
            }
            List<String> matches = matchingLines(readTail(logFile), limit);
            return ToolResult.success(
                    matches.isEmpty()
                            ? "No recent warning or error lines were found."
                            : String.join("\n", matches),
                    Map.of("count", matches.size()));
        } catch (IOException failure) {
            return ToolResult.failure("Unable to read the recent console log.");
        }
    }

    static String redact(String line) {
        String redacted = BEARER.matcher(line).replaceAll("Bearer ***");
        redacted = SECRET.matcher(redacted).replaceAll(match ->
                match.group(1) + "=***");
        return redacted.length() <= MAX_LINE_CHARS
                ? redacted : redacted.substring(0, MAX_LINE_CHARS) + "...";
    }

    private static int limit(Object raw) {
        if (raw instanceof Number number) {
            return Math.max(1, Math.min(MAX_LIMIT, number.intValue()));
        }
        if (raw != null) {
            try {
                return Math.max(1, Math.min(MAX_LIMIT,
                        Integer.parseInt(raw.toString())));
            } catch (NumberFormatException ignored) {
                // Fall through to the default.
            }
        }
        return DEFAULT_LIMIT;
    }

    private static String readTail(Path file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            long length = input.length();
            long start = Math.max(0, length - TAIL_BYTES);
            input.seek(start);
            byte[] bytes = new byte[(int) (length - start)];
            input.readFully(bytes);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (start > 0) {
                int newline = text.indexOf('\n');
                text = newline < 0 ? "" : text.substring(newline + 1);
            }
            return text;
        }
    }

    private static List<String> matchingLines(String text, int limit) {
        String[] lines = text.split("\\R");
        List<String> matches = new ArrayList<>();
        for (int index = lines.length - 1; index >= 0 && matches.size() < limit; index--) {
            String line = lines[index];
            String upper = line.toUpperCase();
            if (upper.contains(" ERROR ") || upper.contains(" WARN ")) {
                matches.add(0, redact(line));
            }
        }
        return List.copyOf(matches);
    }
}
