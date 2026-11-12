package io.github.arbiter.server.agent.tool;

import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;
import io.github.arbiter.agent.spi.tool.AgentTool;
import io.github.arbiter.agent.spi.tool.ToolExecutionContext;
import io.github.arbiter.agent.spi.tool.ToolResult;
import io.github.arbiter.api.crypto.CryptoKeys;
import io.github.arbiter.server.auth.FileUserRepository;
import io.github.arbiter.server.service.StatusService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight health summary that deliberately exposes status only, not file
 * paths, credential contents, or low-level exception messages.
 */
public final class RuntimeHealthTool implements AgentTool {
    private final StatusService statusService;
    private final FileUserRepository userRepository;

    public RuntimeHealthTool(StatusService statusService,
                             FileUserRepository userRepository) {
        this.statusService = statusService;
        this.userRepository = userRepository;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("get_runtime_health")
                .description("Checks service, user-store and crypto-key availability without exposing secrets.")
                .parameters(Map.of("type", "object", "properties", Map.of()))
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
        boolean serviceUp = "UP".equalsIgnoreCase(statusService.status().state());
        boolean userStoreUp = isReadable(userRepository.path());
        boolean cryptoKeyUp = isReadable(CryptoKeys.path());
        boolean overall = serviceUp && userStoreUp && cryptoKeyUp;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("overall", overall ? "UP" : "DOWN");
        data.put("service", serviceUp ? "UP" : "DOWN");
        data.put("userStore", userStoreUp ? "UP" : "DOWN");
        data.put("cryptoKey", cryptoKeyUp ? "UP" : "DOWN");
        return ToolResult.success(
                "overall=" + data.get("overall") + "\n"
                        + "service=" + data.get("service") + "\n"
                        + "userStore=" + data.get("userStore") + "\n"
                        + "cryptoKey=" + data.get("cryptoKey"),
                data);
    }

    private static boolean isReadable(Path path) {
        return path != null
                && Files.isRegularFile(path)
                && Files.isReadable(path);
    }
}
