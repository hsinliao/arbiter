package io.github.arbiter.command.result.payload;

import io.github.arbiter.command.result.ResultPayload;

import java.util.LinkedHashMap;
import java.util.Map;

public record ServiceStatusPayload(String state, String version, String serverTime)
        implements ResultPayload {
    @Override
    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("state", state);
        data.put("version", version);
        data.put("serverTime", serverTime);
        return data;
    }
}
