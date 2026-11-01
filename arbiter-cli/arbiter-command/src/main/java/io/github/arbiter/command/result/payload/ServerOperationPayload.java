package io.github.arbiter.command.result.payload;

import io.github.arbiter.command.result.ResultPayload;

import java.util.LinkedHashMap;
import java.util.Map;

public record ServerOperationPayload(String action, long pid, boolean alreadyRunning)
        implements ResultPayload {
    @Override
    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", action);
        data.put("pid", pid);
        data.put("alreadyRunning", alreadyRunning);
        return data;
    }
}
