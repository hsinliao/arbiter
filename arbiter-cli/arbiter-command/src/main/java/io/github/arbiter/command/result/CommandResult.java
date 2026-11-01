package io.github.arbiter.command.result;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

public record CommandResult(boolean success, String messageKey, Object[] args,
                            Map<String, Object> data, int exitCode) {
    public CommandResult {
        data = data == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        args = args == null ? new Object[0] : args.clone();
    }

    @Override
    public Object[] args() {
        return args.clone();
    }

    public static CommandResult success(String messageKey, Object... args) {
        return new CommandResult(true, messageKey, args, Map.of(), 0);
    }

    public static CommandResult success(String messageKey, Map<String, Object> data,
                                        Object... args) {
        return new CommandResult(true, messageKey, args, data, 0);
    }

    public static CommandResult success(String messageKey, ResultPayload payload,
                                        Object... args) {
        return new CommandResult(true, messageKey, args, payload.toData(), 0);
    }

    public static CommandResult failure(String messageKey, int exitCode, Object... args) {
        return new CommandResult(false, messageKey, args, Map.of(), exitCode);
    }
}
